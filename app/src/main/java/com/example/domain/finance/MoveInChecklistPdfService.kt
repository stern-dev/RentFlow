package com.example.domain.finance

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.example.domain.model.InspectionItemStatus
import com.example.domain.model.Lease
import com.example.domain.model.MoveInConditionChecklist
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.min

internal sealed interface ChecklistPdfBlock {
    data object MetadataAndLeaseBlock : ChecklistPdfBlock
    data class RoomHeaderBlock(val section: ChecklistPdfRoomSection) : ChecklistPdfBlock
    data class RoomItemBlock(val item: ChecklistPdfItemRow) : ChecklistPdfBlock
    data object OverallSummariesBlock : ChecklistPdfBlock
    data object SignatureExecutionBlock : ChecklistPdfBlock
}

object MoveInChecklistPdfService {

    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val MARGIN = 36f
    private const val CONTENT_WIDTH = PAGE_WIDTH - (2f * MARGIN)
    private const val PAGE_ONE_TOP_Y = 96f
    private const val CONTINUATION_TOP_Y = 66f
    private const val FOOTER_RESERVED_Y = PAGE_HEIGHT - 44f

    private const val THUMBNAIL_HEIGHT = 76f
    private const val THUMBNAIL_GAP = 10f
    private const val SIGNATURE_SECTION_HEIGHT = 188f

    suspend fun generateMoveInChecklistPdf(
        context: Context,
        checklist: MoveInConditionChecklist,
        lease: Lease? = null
    ): Result<GeneratedChecklistPdf> = withContext(Dispatchers.IO) {
        runCatching {
            val spec = MoveInChecklistPdfSpecBuilder.buildSpec(checklist, lease)
            val exportDir = File(context.cacheDir, "exports").apply { if (!exists()) mkdirs() }
            val outFile = File(exportDir, spec.suggestedFileName)
            val pageCount = renderSpecToPdfFile(spec = spec, outputFile = outFile)
            GeneratedChecklistPdf(
                file = outFile,
                title = spec.title,
                subtitle = spec.subtitle,
                isFinalizedSigned = spec.isFinalizedSigned,
                statusLabel = spec.statusLabel,
                pageCount = pageCount,
                generatedAtEpochMs = System.currentTimeMillis()
            )
        }
    }

    internal fun buildPagedBlocksForSpec(spec: ChecklistPdfDocumentSpec): List<List<ChecklistPdfBlock>> {
        val pages = mutableListOf<MutableList<ChecklistPdfBlock>>()
        var currentPage = mutableListOf<ChecklistPdfBlock>()
        pages.add(currentPage)
        var y = PAGE_ONE_TOP_Y

        fun startNewPage() {
            currentPage = mutableListOf()
            pages.add(currentPage)
            y = CONTINUATION_TOP_Y
        }

        val paints = PdfPaints()
        val metadataHeight = measureMetadataBlockHeight(spec, paints)
        currentPage.add(ChecklistPdfBlock.MetadataAndLeaseBlock)
        y += metadataHeight + 10f

        spec.roomSections.forEach { room ->
            val roomHeaderHeight = measureRoomHeaderHeight(room, paints)
            val firstItemHeight = room.items.firstOrNull()?.let { measureItemCardHeight(it, paints) } ?: 0f
            val minNeededForRoomStart = roomHeaderHeight + if (room.items.isNotEmpty()) firstItemHeight.coerceAtMost(96f) else 0f
            if (y + minNeededForRoomStart > FOOTER_RESERVED_Y && currentPage.isNotEmpty()) {
                startNewPage()
            }
            currentPage.add(ChecklistPdfBlock.RoomHeaderBlock(room))
            y += roomHeaderHeight + 6f

            room.items.forEach { item ->
                val itemHeight = measureItemCardHeight(item, paints)
                if (y + itemHeight > FOOTER_RESERVED_Y && currentPage.isNotEmpty()) {
                    startNewPage()
                }
                currentPage.add(ChecklistPdfBlock.RoomItemBlock(item))
                y += itemHeight + 7f
            }
            y += 4f
        }

        if (!spec.overallLandlordSummary.isNullOrBlank() || !spec.overallTenantSummary.isNullOrBlank()) {
            val summaryHeight = measureOverallSummariesHeight(spec, paints)
            if (y + summaryHeight > FOOTER_RESERVED_Y && currentPage.isNotEmpty()) {
                startNewPage()
            }
            currentPage.add(ChecklistPdfBlock.OverallSummariesBlock)
            y += summaryHeight + 10f
        }

        if (y + SIGNATURE_SECTION_HEIGHT > FOOTER_RESERVED_Y && currentPage.isNotEmpty()) {
            startNewPage()
        }
        currentPage.add(ChecklistPdfBlock.SignatureExecutionBlock)

        return pages
    }

    private fun renderSpecToPdfFile(
        spec: ChecklistPdfDocumentSpec,
        outputFile: File
    ): Int {
        val doc = PdfDocument()
        val paints = PdfPaints()
        val pagedBlocks = buildPagedBlocksForSpec(spec)
        val totalPages = pagedBlocks.size.coerceAtLeast(1)

        try {
            pagedBlocks.forEachIndexed { pageIndex, blocks ->
                val pageNumber = pageIndex + 1
                val page = doc.startPage(
                    PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
                )
                val canvas = page.canvas

                var y = if (pageNumber == 1) {
                    drawPrimaryBanner(canvas, spec, paints)
                    PAGE_ONE_TOP_Y
                } else {
                    drawContinuationHeader(canvas, spec, paints)
                    CONTINUATION_TOP_Y
                }

                blocks.forEach { block ->
                    y = when (block) {
                        is ChecklistPdfBlock.MetadataAndLeaseBlock ->
                            drawMetadataBlock(canvas, spec, y, paints) + 10f
                        is ChecklistPdfBlock.RoomHeaderBlock ->
                            drawRoomHeader(canvas, block.section, y, paints) + 6f
                        is ChecklistPdfBlock.RoomItemBlock ->
                            drawRoomItemCard(canvas, block.item, y, paints) + 7f
                        is ChecklistPdfBlock.OverallSummariesBlock ->
                            drawOverallSummaries(canvas, spec, y, paints) + 10f
                        is ChecklistPdfBlock.SignatureExecutionBlock ->
                            drawSignatureExecutionSection(canvas, spec, y, paints) + 8f
                    }
                }

                if (!spec.isFinalizedSigned && !spec.watermarkText.isNullOrBlank()) {
                    drawDraftWatermark(canvas, spec.watermarkText, paints)
                }
                drawPageFooter(canvas, spec, pageNumber, totalPages, paints)

                doc.finishPage(page)
            }

            FileOutputStream(outputFile).use { out -> doc.writeTo(out) }
        } finally {
            doc.close()
        }
        return totalPages
    }

    private fun drawPrimaryBanner(canvas: Canvas, spec: ChecklistPdfDocumentSpec, p: PdfPaints) {
        canvas.drawRect(0f, 0f, PAGE_WIDTH.toFloat(), 84f, p.navyFill)
        canvas.drawText(spec.title, MARGIN, 34f, p.bannerTitle)
        canvas.drawText(spec.subtitle, MARGIN, 54f, p.bannerSubtitle)
        val statsText = "${spec.roomSections.size} Rooms • ${spec.totalItems} Items • ${spec.flaggedIssues} Flagged • ${spec.totalPhotos} Photo(s)"
        canvas.drawText(statsText, MARGIN, 71f, p.bannerMeta)

        val pillText = spec.statusLabel
        val pillWidth = p.pillText.measureText(pillText) + 18f
        val pillLeft = (PAGE_WIDTH - MARGIN - pillWidth).coerceAtLeast(MARGIN + 220f)
        val pillRect = RectF(pillLeft, 18f, PAGE_WIDTH - MARGIN, 38f)
        canvas.drawRoundRect(
            pillRect,
            10f,
            10f,
            if (spec.isFinalizedSigned) p.emeraldFill else p.amberFill
        )
        canvas.drawText(pillText, pillRect.left + 9f, 31.5f, p.pillText)
    }

    private fun drawContinuationHeader(canvas: Canvas, spec: ChecklistPdfDocumentSpec, p: PdfPaints) {
        canvas.drawRect(0f, 0f, PAGE_WIDTH.toFloat(), 48f, p.navyFill)
        canvas.drawText(
            "${spec.title} — ${spec.subtitle}",
            MARGIN,
            29f,
            p.continuationTitle
        )
        val tag = if (spec.isFinalizedSigned) "SIGNED REPORT" else "DRAFT PREVIEW"
        val tagW = p.pillText.measureText(tag) + 14f
        val rect = RectF(PAGE_WIDTH - MARGIN - tagW, 14f, PAGE_WIDTH - MARGIN, 33f)
        canvas.drawRoundRect(rect, 8f, 8f, if (spec.isFinalizedSigned) p.emeraldFill else p.amberFill)
        canvas.drawText(tag, rect.left + 7f, 27f, p.pillText)
    }

    private fun drawPageFooter(
        canvas: Canvas,
        spec: ChecklistPdfDocumentSpec,
        pageNumber: Int,
        totalPages: Int,
        p: PdfPaints
    ) {
        val lineY = PAGE_HEIGHT - 32f
        canvas.drawLine(MARGIN, lineY, PAGE_WIDTH - MARGIN, lineY, p.borderStroke)
        val leftText = if (spec.isFinalizedSigned) {
            "RentFlow Executed Move-In Inspection • Mutually Signed"
        } else {
            "RentFlow Draft Preview • Not Finalized Until Both Parties Sign"
        }
        canvas.drawText(leftText, MARGIN, PAGE_HEIGHT - 17f, p.footerText)
        val pageLabel = "Page $pageNumber of $totalPages"
        val pageLabelW = p.footerText.measureText(pageLabel)
        canvas.drawText(pageLabel, PAGE_WIDTH - MARGIN - pageLabelW, PAGE_HEIGHT - 17f, p.footerText)
    }

    private fun drawDraftWatermark(canvas: Canvas, watermarkText: String, p: PdfPaints) {
        canvas.save()
        canvas.translate(PAGE_WIDTH / 2f, PAGE_HEIGHT / 2f)
        canvas.rotate(-34f)
        val textWidth = p.watermarkText.measureText(watermarkText)
        canvas.drawText(watermarkText, -textWidth / 2f, -90f, p.watermarkText)
        canvas.drawText(watermarkText, -textWidth / 2f, 110f, p.watermarkText)
        canvas.restore()
    }

    private fun measureMetadataBlockHeight(spec: ChecklistPdfDocumentSpec, p: PdfPaints): Float {
        val rowCount = (spec.metadataRows.size + 1) / 2
        val gridHeight = rowCount * 26f + 18f
        val leaseStripHeight = if (spec.leaseSummaryLine.isNullOrBlank()) {
            0f
        } else {
            val layout = buildStaticLayout(spec.leaseSummaryLine, p.smallBody, (CONTENT_WIDTH - 20f).toInt())
            layout.height + 16f
        }
        return gridHeight + leaseStripHeight
    }

    private fun drawMetadataBlock(
        canvas: Canvas,
        spec: ChecklistPdfDocumentSpec,
        startY: Float,
        p: PdfPaints
    ): Float {
        val totalHeight = measureMetadataBlockHeight(spec, p)
        val cardRect = RectF(MARGIN, startY, PAGE_WIDTH - MARGIN, startY + totalHeight)
        canvas.drawRoundRect(cardRect, 8f, 8f, p.softCardFill)
        canvas.drawRoundRect(cardRect, 8f, 8f, p.borderStroke)

        val colW = (CONTENT_WIDTH - 28f) / 2f
        var y = startY + 14f
        spec.metadataRows.chunked(2).forEach { pair ->
            pair.forEachIndexed { colIdx, item ->
                val x = MARGIN + 10f + colIdx * (colW + 8f)
                canvas.drawText(item.label.uppercase(), x, y, p.metaLabel)
                canvas.drawText(item.value.take(42), x, y + 11.5f, p.metaValue)
            }
            y += 26f
        }

        if (!spec.leaseSummaryLine.isNullOrBlank()) {
            canvas.drawLine(MARGIN + 10f, y - 4f, PAGE_WIDTH - MARGIN - 10f, y - 4f, p.borderStroke)
            y += 4f
            val layout = buildStaticLayout(spec.leaseSummaryLine, p.smallBody, (CONTENT_WIDTH - 20f).toInt())
            drawStaticLayout(canvas, layout, MARGIN + 10f, y)
        }
        return startY + totalHeight
    }

    private fun measureRoomHeaderHeight(room: ChecklistPdfRoomSection, p: PdfPaints): Float {
        val baseH = 26f
        if (room.roomRemarks.isBlank()) return baseH
        val remarksLayout = buildStaticLayout(
            "Room Notes: ${room.roomRemarks}",
            p.smallBody,
            (CONTENT_WIDTH - 20f).toInt()
        )
        return baseH + remarksLayout.height + 6f
    }

    private fun drawRoomHeader(
        canvas: Canvas,
        room: ChecklistPdfRoomSection,
        startY: Float,
        p: PdfPaints
    ): Float {
        val totalH = measureRoomHeaderHeight(room, p)
        val rect = RectF(MARGIN, startY, PAGE_WIDTH - MARGIN, startY + totalH)
        canvas.drawRoundRect(rect, 6f, 6f, p.roomHeaderFill)
        canvas.drawText(room.roomName.uppercase(), MARGIN + 10f, startY + 17f, p.roomHeaderTitle)
        val countLabel = "${room.items.size} item(s)"
        val countW = p.metaLabel.measureText(countLabel)
        canvas.drawText(countLabel, PAGE_WIDTH - MARGIN - 10f - countW, startY + 16.5f, p.metaLabel)

        if (room.roomRemarks.isNotBlank()) {
            val remarksLayout = buildStaticLayout(
                "Room Notes: ${room.roomRemarks}",
                p.smallBody,
                (CONTENT_WIDTH - 20f).toInt()
            )
            drawStaticLayout(canvas, remarksLayout, MARGIN + 10f, startY + 24f)
        }
        return startY + totalH
    }

    private fun measureItemCardHeight(item: ChecklistPdfItemRow, p: PdfPaints): Float {
        val textW = (CONTENT_WIDTH - 20f).toInt()
        var height = 10f // top pad
        val titleLayout = buildStaticLayout(item.label, p.itemTitle, (CONTENT_WIDTH - 125f).toInt())
        height += titleLayout.height.coerceAtLeast(16) + 6f

        if (item.landlordRemarks.isNotBlank()) {
            val lLayout = buildStaticLayout("Management: ${item.landlordRemarks}", p.smallBody, textW)
            height += lLayout.height + 4f
        }
        if (item.tenantRemarks.isNotBlank()) {
            val tLayout = buildStaticLayout("Tenant: ${item.tenantRemarks}", p.smallBody, textW)
            height += tLayout.height + 4f
        }
        if (item.photoDataUris.isNotEmpty()) {
            height += THUMBNAIL_HEIGHT + 8f
        }
        return height + 6f
    }

    private fun drawRoomItemCard(
        canvas: Canvas,
        item: ChecklistPdfItemRow,
        startY: Float,
        p: PdfPaints
    ): Float {
        val totalH = measureItemCardHeight(item, p)
        val rect = RectF(MARGIN, startY, PAGE_WIDTH - MARGIN, startY + totalH)
        canvas.drawRoundRect(rect, 6f, 6f, p.whiteFill)
        canvas.drawRoundRect(rect, 6f, 6f, p.borderStroke)

        val titleLayout = buildStaticLayout(item.label, p.itemTitle, (CONTENT_WIDTH - 125f).toInt())
        drawStaticLayout(canvas, titleLayout, MARGIN + 10f, startY + 10f)

        // Status pill top-right
        val statusLabel = item.status.label.uppercase()
        val pillW = p.statusPillText.measureText(statusLabel) + 14f
        val pillRect = RectF(PAGE_WIDTH - MARGIN - 10f - pillW, startY + 8f, PAGE_WIDTH - MARGIN - 10f, startY + 24f)
        val (bgPaint, fgColor) = statusPaints(item.status, p)
        canvas.drawRoundRect(pillRect, 8f, 8f, bgPaint)
        p.statusPillText.color = fgColor
        canvas.drawText(statusLabel, pillRect.left + 7f, startY + 19f, p.statusPillText)

        var cursorY = startY + 10f + titleLayout.height.coerceAtLeast(16) + 6f
        val textW = (CONTENT_WIDTH - 20f).toInt()

        if (item.landlordRemarks.isNotBlank()) {
            val lLayout = buildStaticLayout("Management: ${item.landlordRemarks}", p.smallBody, textW)
            drawStaticLayout(canvas, lLayout, MARGIN + 10f, cursorY)
            cursorY += lLayout.height + 4f
        }
        if (item.tenantRemarks.isNotBlank()) {
            val tLayout = buildStaticLayout("Tenant: ${item.tenantRemarks}", p.smallBody, textW)
            drawStaticLayout(canvas, tLayout, MARGIN + 10f, cursorY)
            cursorY += tLayout.height + 4f
        }
        if (item.photoDataUris.isNotEmpty()) {
            val thumbW = (CONTENT_WIDTH - 20f - THUMBNAIL_GAP) / 2f
            item.photoDataUris.forEachIndexed { idx, uri ->
                val left = MARGIN + 10f + idx * (thumbW + THUMBNAIL_GAP)
                val thumbFrame = RectF(left, cursorY + 2f, left + thumbW, cursorY + 2f + THUMBNAIL_HEIGHT)
                canvas.drawRoundRect(thumbFrame, 5f, 5f, p.softCardFill)
                canvas.drawRoundRect(thumbFrame, 5f, 5f, p.borderStroke)

                val bmp = MoveInChecklistPdfSpecBuilder.decodeInlineImageDataUri(uri, maxDimensionPx = 420)
                if (bmp != null) {
                    val dest = computeAspectFitRect(
                        bmpWidth = bmp.width,
                        bmpHeight = bmp.height,
                        bounds = RectF(thumbFrame.left + 3f, thumbFrame.top + 3f, thumbFrame.right - 3f, thumbFrame.bottom - 3f)
                    )
                    canvas.drawBitmap(bmp, Rect(0, 0, bmp.width, bmp.height), dest, p.bitmapPaint)
                    bmp.recycle()
                } else {
                    canvas.drawText("Photo unavailable", thumbFrame.left + 12f, thumbFrame.centerY() + 3f, p.pendingText)
                }
            }
        }
        return startY + totalH
    }

    private fun measureOverallSummariesHeight(spec: ChecklistPdfDocumentSpec, p: PdfPaints): Float {
        var h = 28f
        val textW = (CONTENT_WIDTH - 20f).toInt()
        spec.overallLandlordSummary?.let {
            h += buildStaticLayout("Landlord Overall Summary: $it", p.smallBody, textW).height + 6f
        }
        spec.overallTenantSummary?.let {
            h += buildStaticLayout("Tenant Overall Summary: $it", p.smallBody, textW).height + 6f
        }
        return h + 8f
    }

    private fun drawOverallSummaries(
        canvas: Canvas,
        spec: ChecklistPdfDocumentSpec,
        startY: Float,
        p: PdfPaints
    ): Float {
        val totalH = measureOverallSummariesHeight(spec, p)
        val rect = RectF(MARGIN, startY, PAGE_WIDTH - MARGIN, startY + totalH)
        canvas.drawRoundRect(rect, 8f, 8f, p.softCardFill)
        canvas.drawRoundRect(rect, 8f, 8f, p.borderStroke)
        canvas.drawText("OVERALL HANDOVER SUMMARIES", MARGIN + 10f, startY + 18f, p.sectionTitle)

        var y = startY + 26f
        val textW = (CONTENT_WIDTH - 20f).toInt()
        spec.overallLandlordSummary?.let { text ->
            val layout = buildStaticLayout("Landlord Overall Summary: $text", p.smallBody, textW)
            drawStaticLayout(canvas, layout, MARGIN + 10f, y)
            y += layout.height + 6f
        }
        spec.overallTenantSummary?.let { text ->
            val layout = buildStaticLayout("Tenant Overall Summary: $text", p.smallBody, textW)
            drawStaticLayout(canvas, layout, MARGIN + 10f, y)
        }
        return startY + totalH
    }

    private fun drawSignatureExecutionSection(
        canvas: Canvas,
        spec: ChecklistPdfDocumentSpec,
        startY: Float,
        p: PdfPaints
    ): Float {
        canvas.drawLine(MARGIN, startY, PAGE_WIDTH - MARGIN, startY, p.borderStroke)
        val heading = if (spec.isFinalizedSigned) {
            "EXECUTED DUAL-PARTY DIGITAL SIGNATURES (INSPECTION & LEASE REPORT)"
        } else {
            "SIGNATURE EXECUTION STATUS (DRAFT / INCOMPLETE)"
        }
        canvas.drawText(heading, MARGIN, startY + 18f, p.sectionTitle)

        val gap = 12f
        val boxW = (CONTENT_WIDTH - gap) / 2f
        val boxTop = startY + 28f
        val boxH = 148f

        spec.signatureBlocks.take(2).forEachIndexed { index, block ->
            val left = MARGIN + index * (boxW + gap)
            val cardRect = RectF(left, boxTop, left + boxW, boxTop + boxH)
            canvas.drawRoundRect(cardRect, 8f, 8f, p.softCardFill)
            canvas.drawRoundRect(cardRect, 8f, 8f, p.borderStroke)

            canvas.drawText(block.roleTitle.take(34), left + 10f, boxTop + 17f, p.sectionTitle)

            val sigRect = RectF(left + 10f, boxTop + 25f, left + boxW - 10f, boxTop + 88f)
            canvas.drawRoundRect(sigRect, 4f, 4f, p.whiteFill)
            canvas.drawRoundRect(sigRect, 4f, 4f, p.borderStroke)

            val sig = block.signature
            if (sig != null) {
                val bmp = MoveInChecklistPdfSpecBuilder.decodeInlineImageDataUri(sig.signatureImageRef, maxDimensionPx = 600)
                if (bmp != null) {
                    val dest = computeAspectFitRect(
                        bmpWidth = bmp.width,
                        bmpHeight = bmp.height,
                        bounds = RectF(sigRect.left + 4f, sigRect.top + 4f, sigRect.right - 4f, sigRect.bottom - 4f)
                    )
                    canvas.drawBitmap(bmp, Rect(0, 0, bmp.width, bmp.height), dest, p.bitmapPaint)
                    bmp.recycle()
                } else {
                    canvas.drawText("[Signed Digitally]", left + 18f, sigRect.centerY() + 3f, p.smallBody)
                }

                canvas.drawText("Name: ${sig.signerName.take(34)}", left + 10f, boxTop + 104f, p.metaValue)
                canvas.drawText("Role: ${sig.signerRole}", left + 10f, boxTop + 118f, p.smallBody)
                val dateText = CurrencyUtils.formatDateTime(sig.signedAtEpochMs)
                val scopeText = if (sig.coversInspectionReport && sig.coversLeaseReport) {
                    "Inspection & Lease"
                } else {
                    "Condition Report"
                }
                canvas.drawText("$dateText • $scopeText".take(42), left + 10f, boxTop + 132f, p.metaLabel)
            } else {
                canvas.drawText("Pending Signature", left + 18f, sigRect.centerY() + 3f, p.pendingText)
                canvas.drawText("Name: ______________________", left + 10f, boxTop + 104f, p.pendingText)
                canvas.drawText("Date: Pending execution", left + 10f, boxTop + 120f, p.pendingText)
            }
        }
        return boxTop + boxH
    }

    private fun statusPaints(status: InspectionItemStatus, p: PdfPaints): Pair<Paint, Int> = when (status) {
        InspectionItemStatus.EXCELLENT,
        InspectionItemStatus.GOOD -> p.emeraldSoftFill to Color.rgb(4, 120, 87)
        InspectionItemStatus.FAIR -> p.slateSoftFill to Color.rgb(30, 64, 175)
        InspectionItemStatus.NEEDS_REPAIR -> p.amberSoftFill to Color.rgb(180, 83, 9)
        InspectionItemStatus.DAMAGED,
        InspectionItemStatus.MISSING -> p.redSoftFill to Color.rgb(185, 28, 28)
        InspectionItemStatus.NOT_APPLICABLE -> p.softCardFill to Color.rgb(71, 85, 105)
    }

    private fun buildStaticLayout(text: CharSequence, paint: TextPaint, width: Int): StaticLayout {
        val safeW = width.coerceAtLeast(40)
        return StaticLayout.Builder.obtain(text, 0, text.length, paint, safeW)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(1.5f, 1.0f)
            .setIncludePad(false)
            .build()
    }

    private fun drawStaticLayout(canvas: Canvas, layout: StaticLayout, x: Float, y: Float) {
        canvas.save()
        canvas.translate(x, y)
        layout.draw(canvas)
        canvas.restore()
    }

    internal fun computeAspectFitRect(bmpWidth: Int, bmpHeight: Int, bounds: RectF): RectF {
        if (bmpWidth <= 0 || bmpHeight <= 0) return bounds
        val scale = min(bounds.width() / bmpWidth.toFloat(), bounds.height() / bmpHeight.toFloat())
        val drawnW = bmpWidth * scale
        val drawnH = bmpHeight * scale
        val left = bounds.left + (bounds.width() - drawnW) / 2f
        val top = bounds.top + (bounds.height() - drawnH) / 2f
        return RectF(left, top, left + drawnW, top + drawnH)
    }

    private class PdfPaints {
        val navyFill = Paint().apply { color = Color.rgb(11, 31, 58); style = Paint.Style.FILL }
        val emeraldFill = Paint().apply { color = Color.rgb(13, 148, 136); style = Paint.Style.FILL }
        val amberFill = Paint().apply { color = Color.rgb(217, 119, 6); style = Paint.Style.FILL }
        val whiteFill = Paint().apply { color = Color.WHITE; style = Paint.Style.FILL }
        val softCardFill = Paint().apply { color = Color.rgb(248, 250, 252); style = Paint.Style.FILL }
        val roomHeaderFill = Paint().apply { color = Color.rgb(226, 232, 240); style = Paint.Style.FILL }
        val emeraldSoftFill = Paint().apply { color = Color.rgb(209, 250, 229); style = Paint.Style.FILL }
        val slateSoftFill = Paint().apply { color = Color.rgb(219, 234, 254); style = Paint.Style.FILL }
        val amberSoftFill = Paint().apply { color = Color.rgb(254, 243, 199); style = Paint.Style.FILL }
        val redSoftFill = Paint().apply { color = Color.rgb(254, 226, 226); style = Paint.Style.FILL }
        val borderStroke = Paint().apply { color = Color.rgb(203, 213, 225); style = Paint.Style.STROKE; strokeWidth = 0.9f }
        val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        val bannerTitle = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 15.5f; isFakeBoldText = true }
        val bannerSubtitle = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(204, 251, 241); textSize = 10.5f }
        val bannerMeta = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(203, 213, 225); textSize = 8.5f }
        val continuationTitle = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 11f; isFakeBoldText = true }
        val pillText = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 8.5f; isFakeBoldText = true }
        val statusPillText = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 7.8f; isFakeBoldText = true }
        val roomHeaderTitle = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(11, 31, 58); textSize = 10.5f; isFakeBoldText = true }
        val sectionTitle = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(11, 31, 58); textSize = 9.5f; isFakeBoldText = true }
        val itemTitle = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(15, 23, 42); textSize = 9.5f; isFakeBoldText = true }
        val smallBody = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(51, 65, 85); textSize = 8.8f }
        val metaLabel = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(100, 116, 139); textSize = 7.4f; isFakeBoldText = true }
        val metaValue = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(15, 23, 42); textSize = 9f; isFakeBoldText = true }
        val pendingText = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(148, 163, 184); textSize = 8.8f }
        val footerText = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(100, 116, 139); textSize = 7.8f }
        val watermarkText = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(46, 217, 119, 6)
            textSize = 38f
            isFakeBoldText = true
        }
    }
}
