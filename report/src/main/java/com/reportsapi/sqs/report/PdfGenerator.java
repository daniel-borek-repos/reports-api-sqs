package com.reportsapi.sqs.report;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.CategoryAxis;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.block.BlockBorder;
import org.jfree.chart.plot.CategoryPlot;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.renderer.category.BarRenderer;
import org.jfree.chart.renderer.category.StandardBarPainter;
import org.jfree.data.category.DefaultCategoryDataset;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public class PdfGenerator {

    private PdfGenerator() {}

    private static final float W  = PDRectangle.A4.getWidth();
    private static final float H  = PDRectangle.A4.getHeight();
    private static final float M  = 40f;
    private static final float CW = W - 2 * M;

    private static final Color C_HEADER  = new Color(44,  62,  80);
    private static final Color C_BUGS    = new Color(230, 126, 34);
    private static final Color C_VULN    = new Color(231, 76,  60);
    private static final Color C_SMELLS  = new Color(52,  152, 219);
    private static final Color C_PLOT_BG = new Color(248, 249, 250);
    private static final Color C_GRID    = new Color(210, 215, 220);
    private static final Color C_SUBTEXT = new Color(150, 170, 190);

    private static final int TOP_N = 30;

    public static void generate(List<CsvRecord> records, File out) throws Exception {
        List<CsvRecord> top = records.stream()
                .sorted(Comparator.comparingInt(r ->
                        -(r.getBugs() + r.getVulnerabilities() + r.getCodeSmells())))
                .limit(TOP_N)
                .collect(Collectors.toList());

        try (PDDocument doc = new PDDocument()) {
            PDFont bold    = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            PDFont regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            addSummaryPage(doc, records, top, bold, regular);
            doc.save(out);
        }
    }

    private static void addSummaryPage(PDDocument doc, List<CsvRecord> all,
                                        List<CsvRecord> top,
                                        PDFont bold, PDFont regular) throws Exception {
        PDPage page = new PDPage(PDRectangle.A4);
        doc.addPage(page);

        long totBugs   = all.stream().mapToLong(CsvRecord::getBugs).sum();
        long totVuln   = all.stream().mapToLong(CsvRecord::getVulnerabilities).sum();
        long totSmells = all.stream().mapToLong(CsvRecord::getCodeSmells).sum();
        long totNcloc  = all.stream().mapToLong(CsvRecord::getNcloc).sum();

        float headerH = 75f;
        float cardGap = 10f;
        float cardH   = 70f;
        float cardW   = (CW - 3 * cardGap) / 4f;
        float cardsY  = H - headerH - cardGap - cardH;
        float labelY  = cardsY - 28f;
        float chartH  = labelY - 14f - M;

        try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
            fillRect(cs, 0, H - headerH, W, headerH, C_HEADER);
            text(cs, "SonarQube Server Quality Report",
                    M, H - headerH + 34f, bold, 22f, Color.WHITE);
            text(cs, "Generated " + LocalDateTime.now()
                            .format(DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm")),
                    M, H - headerH + 13f, regular, 10f, C_SUBTEXT);

            float cx = M;
            drawCard(cs, cx, cardsY, cardW, cardH,
                    "Projects", String.valueOf(all.size()),
                    bold, regular, new Color(52, 73, 94));
            cx += cardW + cardGap;
            drawCard(cs, cx, cardsY, cardW, cardH,
                    "Total Issues", fmtLong(totBugs + totVuln + totSmells),
                    bold, regular, new Color(142, 68, 173));
            cx += cardW + cardGap;
            drawCard(cs, cx, cardsY, cardW, cardH,
                    "Vulnerabilities", fmtLong(totVuln),
                    bold, regular, new Color(192, 57, 43));
            cx += cardW + cardGap;
            drawCard(cs, cx, cardsY, cardW, cardH,
                    "Lines of Code", fmtLong(totNcloc),
                    bold, regular, new Color(39, 174, 96));

            String label = "Issues by Project"
                    + (all.size() > TOP_N ? " \u2014 top " + TOP_N + " by issue count" : "");
            text(cs, label, M, labelY, bold, 13f, C_HEADER);
            cs.setStrokingColor(C_GRID);
            cs.setLineWidth(0.5f);
            cs.moveTo(M, labelY - 6f);
            cs.lineTo(W - M, labelY - 6f);
            cs.stroke();

            drawChart(doc, cs, buildChart(top), M, M, CW, chartH);
        }
    }

    private static JFreeChart buildChart(List<CsvRecord> projects) {
        DefaultCategoryDataset ds = new DefaultCategoryDataset();
        for (CsvRecord r : projects) {
            String name = shorten(r.getProjectName() != null ? r.getProjectName() : r.getProjectKey(), 36);
            ds.addValue(r.getBugs(),             "Bugs",             name);
            ds.addValue(r.getVulnerabilities(),  "Vulnerabilities",  name);
            ds.addValue(r.getCodeSmells(),       "Code Smells",      name);
        }
        return styleChart(
                ChartFactory.createBarChart(null, null, "Issues",
                        ds, PlotOrientation.HORIZONTAL, true, false, false),
                new Color[]{C_BUGS, C_VULN, C_SMELLS});
    }

    private static JFreeChart styleChart(JFreeChart chart, Color[] colors) {
        chart.setBackgroundPaint(Color.WHITE);
        chart.setBorderVisible(false);

        if (chart.getLegend() != null) {
            chart.getLegend().setBackgroundPaint(Color.WHITE);
            chart.getLegend().setFrame(BlockBorder.NONE);
            chart.getLegend().setItemFont(new Font("SansSerif", Font.PLAIN, 12));
        }

        CategoryPlot plot = chart.getCategoryPlot();
        plot.setBackgroundPaint(C_PLOT_BG);
        plot.setOutlineVisible(false);
        plot.setDomainGridlinesVisible(false);
        plot.setRangeGridlinePaint(C_GRID);
        plot.setRangeGridlineStroke(new BasicStroke(0.5f));

        BarRenderer rend = (BarRenderer) plot.getRenderer();
        rend.setBarPainter(new StandardBarPainter());
        rend.setShadowVisible(false);
        rend.setItemMargin(0.08);
        for (int i = 0; i < colors.length; i++) rend.setSeriesPaint(i, colors[i]);

        CategoryAxis cAxis = plot.getDomainAxis();
        cAxis.setAxisLineVisible(false);
        cAxis.setTickLabelFont(new Font("SansSerif", Font.PLAIN, 11));

        NumberAxis nAxis = (NumberAxis) plot.getRangeAxis();
        nAxis.setAxisLineVisible(false);
        nAxis.setTickLabelFont(new Font("SansSerif", Font.PLAIN, 11));
        nAxis.setStandardTickUnits(NumberAxis.createIntegerTickUnits());

        return chart;
    }

    private static void drawChart(PDDocument doc, PDPageContentStream cs,
                                   JFreeChart chart,
                                   float x, float y, float w, float h) throws Exception {
        BufferedImage img = chart.createBufferedImage(Math.round(w * 2), Math.round(h * 2));
        PDImageXObject pdImg = LosslessFactory.createFromImage(doc, img);
        cs.drawImage(pdImg, x, y, w, h);
    }

    private static void drawCard(PDPageContentStream cs,
                                  float x, float y, float w, float h,
                                  String label, String value,
                                  PDFont bold, PDFont regular, Color bg) throws Exception {
        fillRect(cs, x, y, w, h, bg);
        float vs = 20f, ls = 8f;
        float vw = bold.getStringWidth(ascii(value))    / 1000f * vs;
        float lw = regular.getStringWidth(ascii(label)) / 1000f * ls;
        text(cs, ascii(value), x + (w - vw) / 2f, y + h - 28f, bold,    vs, Color.WHITE);
        text(cs, ascii(label), x + (w - lw) / 2f, y + 10f,     regular, ls, new Color(190, 210, 230));
    }

    private static void fillRect(PDPageContentStream cs,
                                  float x, float y, float w, float h, Color c) throws Exception {
        cs.setNonStrokingColor(c);
        cs.addRect(x, y, w, h);
        cs.fill();
    }

    private static void text(PDPageContentStream cs, String str,
                              float x, float y, PDFont font, float size, Color c) throws Exception {
        cs.setNonStrokingColor(c);
        cs.beginText();
        cs.setFont(font, size);
        cs.newLineAtOffset(x, y);
        cs.showText(str);
        cs.endText();
    }

    private static String ascii(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length());
        for (char c : s.toCharArray()) sb.append((c >= 32 && c <= 126) ? c : '?');
        return sb.toString();
    }

    private static String shorten(String s, int max) {
        String clean = ascii(s);
        return clean.length() <= max ? clean : clean.substring(0, max - 3) + "...";
    }

    private static String fmtLong(long n) {
        if (n >= 1_000_000L) return String.format(Locale.US, "%.1fM", n / 1_000_000.0);
        if (n >= 1_000L)     return String.format(Locale.US, "%.1fK", n / 1_000.0);
        return String.valueOf(n);
    }
}
