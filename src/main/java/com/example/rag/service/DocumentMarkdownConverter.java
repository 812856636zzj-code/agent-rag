package com.example.rag.service;

import com.example.rag.dto.DocumentMarkdown;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.hslf.usermodel.HSLFShape;
import org.apache.poi.hslf.usermodel.HSLFSlide;
import org.apache.poi.hslf.usermodel.HSLFSlideShow;
import org.apache.poi.hslf.usermodel.HSLFTable;
import org.apache.poi.hslf.usermodel.HSLFTableCell;
import org.apache.poi.hslf.usermodel.HSLFTextShape;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFShape;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTable;
import org.apache.poi.xslf.usermodel.XSLFTableCell;
import org.apache.poi.xslf.usermodel.XSLFTableRow;
import org.apache.poi.xslf.usermodel.XSLFTextShape;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class DocumentMarkdownConverter {

    private static final Pattern NUMBERED_HEADING = Pattern.compile("^((第[一二三四五六七八九十百千0-9]+[章节篇])|([0-9]+(\\.[0-9]+){0,3}))\\s*[^\\s].*");

    public DocumentMarkdown convert(MultipartFile file, String originalFilename) throws IOException {
        String fileType = resolveFileType(originalFilename);
        String markdown;
        switch (fileType) {
            case "txt":
            case "md":
                markdown = normalizeText(new String(file.getBytes(), StandardCharsets.UTF_8));
                break;
            case "pdf":
                markdown = pdfToMarkdown(file);
                break;
            case "docx":
                markdown = docxToMarkdown(file);
                break;
            case "doc":
                markdown = docToMarkdown(file);
                break;
            case "pptx":
                markdown = pptxToMarkdown(file);
                break;
            case "ppt":
                markdown = pptToMarkdown(file);
                break;
            default:
                throw new IllegalArgumentException("unsupported file type: " + fileType);
        }
        return new DocumentMarkdown(fileType, markdown.trim());
    }

    public boolean supports(String originalFilename) {
        String fileType = resolveFileType(originalFilename);
        return "txt".equals(fileType) || "md".equals(fileType) || "pdf".equals(fileType)
                || "docx".equals(fileType) || "doc".equals(fileType)
                || "pptx".equals(fileType) || "ppt".equals(fileType);
    }

    public String supportedFileTypesText() {
        return "supported file types: txt, md, pdf, doc, docx, ppt, pptx";
    }

    private String pdfToMarkdown(MultipartFile file) throws IOException {
        StringBuilder markdown = new StringBuilder();
        try (PDDocument pdf = PDDocument.load(file.getInputStream())) {
            PDFTextStripper stripper = new PDFTextStripper();
            int pageCount = pdf.getNumberOfPages();
            for (int page = 1; page <= pageCount; page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                appendPage(markdown, "Page " + page, stripper.getText(pdf));
            }
        }
        return markdown.toString();
    }

    private String docxToMarkdown(MultipartFile file) throws IOException {
        StringBuilder markdown = new StringBuilder();
        try (XWPFDocument document = new XWPFDocument(file.getInputStream())) {
            for (IBodyElement element : document.getBodyElements()) {
                if (element instanceof XWPFParagraph) {
                    appendWordParagraph(markdown, (XWPFParagraph) element);
                } else if (element instanceof XWPFTable) {
                    appendMarkdownTable(markdown, toRows((XWPFTable) element));
                }
            }
        }
        return markdown.toString();
    }

    private String docToMarkdown(MultipartFile file) throws IOException {
        StringBuilder markdown = new StringBuilder();
        try (HWPFDocument document = new HWPFDocument(file.getInputStream());
             WordExtractor extractor = new WordExtractor(document)) {
            String[] paragraphs = extractor.getParagraphText();
            for (String paragraph : paragraphs) {
                appendLineAsMarkdownBlock(markdown, paragraph);
            }
        }
        return markdown.toString();
    }

    private String pptxToMarkdown(MultipartFile file) throws IOException {
        StringBuilder markdown = new StringBuilder();
        try (XMLSlideShow slideShow = new XMLSlideShow(file.getInputStream())) {
            List<XSLFSlide> slides = slideShow.getSlides();
            for (int i = 0; i < slides.size(); i++) {
                markdown.append("## Slide ").append(i + 1).append("\n\n");
                int textShapeIndex = 0;
                for (XSLFShape shape : slides.get(i).getShapes()) {
                    if (shape instanceof XSLFTable) {
                        appendMarkdownTable(markdown, toRows((XSLFTable) shape));
                    } else if (shape instanceof XSLFTextShape) {
                        appendSlideText(markdown, ((XSLFTextShape) shape).getText(), textShapeIndex == 0);
                        textShapeIndex++;
                    }
                }
            }
        }
        return markdown.toString();
    }

    private String pptToMarkdown(MultipartFile file) throws IOException {
        StringBuilder markdown = new StringBuilder();
        try (HSLFSlideShow slideShow = new HSLFSlideShow(file.getInputStream())) {
            List<HSLFSlide> slides = slideShow.getSlides();
            for (int i = 0; i < slides.size(); i++) {
                markdown.append("## Slide ").append(i + 1).append("\n\n");
                int textShapeIndex = 0;
                for (HSLFShape shape : slides.get(i).getShapes()) {
                    if (shape instanceof HSLFTable) {
                        appendMarkdownTable(markdown, toRows((HSLFTable) shape));
                    } else if (shape instanceof HSLFTextShape) {
                        appendSlideText(markdown, ((HSLFTextShape) shape).getText(), textShapeIndex == 0);
                        textShapeIndex++;
                    }
                }
            }
        }
        return markdown.toString();
    }

    private void appendWordParagraph(StringBuilder markdown, XWPFParagraph paragraph) {
        String text = normalizeInline(paragraph.getText());
        if (!StringUtils.hasText(text)) {
            return;
        }
        String style = paragraph.getStyle();
        Integer level = headingLevelFromStyle(style);
        if (level != null) {
            markdown.append(repeat("#", level)).append(" ").append(text).append("\n\n");
            return;
        }
        appendLineAsMarkdownBlock(markdown, text);
    }

    private void appendPage(StringBuilder markdown, String pageTitle, String text) {
        markdown.append("## ").append(pageTitle).append("\n\n");
        String[] lines = text == null ? new String[0] : text.split("\\R");
        for (String line : lines) {
            appendLineAsMarkdownBlock(markdown, line);
        }
    }

    private void appendSlideText(StringBuilder markdown, String text, boolean titleCandidate) {
        String normalized = normalizeInline(text);
        if (!StringUtils.hasText(normalized)) {
            return;
        }
        if (titleCandidate) {
            markdown.append("### ").append(normalized).append("\n\n");
            return;
        }
        String[] lines = normalized.split("\\R");
        for (String line : lines) {
            appendLineAsMarkdownBlock(markdown, line);
        }
    }

    private void appendLineAsMarkdownBlock(StringBuilder markdown, String rawLine) {
        String line = normalizeInline(rawLine);
        if (!StringUtils.hasText(line)) {
            return;
        }
        if (line.startsWith("#") || line.startsWith("|") || line.startsWith("- ") || line.startsWith("* ")) {
            markdown.append(line).append("\n\n");
            return;
        }
        Integer level = inferHeadingLevel(line);
        if (level != null) {
            markdown.append(repeat("#", level)).append(" ").append(line).append("\n\n");
        } else {
            markdown.append(line).append("\n\n");
        }
    }

    private void appendMarkdownTable(StringBuilder markdown, List<List<String>> rows) {
        List<List<String>> cleanedRows = rows.stream()
                .map(row -> row.stream().map(this::normalizeTableCell).collect(Collectors.toList()))
                .filter(row -> row.stream().anyMatch(StringUtils::hasText))
                .collect(Collectors.toList());
        if (cleanedRows.isEmpty()) {
            return;
        }
        int columnCount = cleanedRows.stream().mapToInt(List::size).max().orElse(0);
        if (columnCount == 0) {
            return;
        }
        for (List<String> row : cleanedRows) {
            while (row.size() < columnCount) {
                row.add("");
            }
        }
        appendTableRow(markdown, cleanedRows.get(0));
        List<String> separator = new ArrayList<>();
        for (int i = 0; i < columnCount; i++) {
            separator.add("---");
        }
        appendTableRow(markdown, separator);
        for (int i = 1; i < cleanedRows.size(); i++) {
            appendTableRow(markdown, cleanedRows.get(i));
        }
        markdown.append("\n");
    }

    private void appendTableRow(StringBuilder markdown, List<String> cells) {
        markdown.append("| ").append(String.join(" | ", cells)).append(" |\n");
    }

    private List<List<String>> toRows(XWPFTable table) {
        List<List<String>> rows = new ArrayList<>();
        for (XWPFTableRow row : table.getRows()) {
            List<String> cells = new ArrayList<>();
            for (XWPFTableCell cell : row.getTableCells()) {
                cells.add(cell.getText());
            }
            rows.add(cells);
        }
        return rows;
    }

    private List<List<String>> toRows(XSLFTable table) {
        List<List<String>> rows = new ArrayList<>();
        for (XSLFTableRow row : table.getRows()) {
            List<String> cells = new ArrayList<>();
            for (XSLFTableCell cell : row.getCells()) {
                cells.add(cell.getText());
            }
            rows.add(cells);
        }
        return rows;
    }

    private List<List<String>> toRows(HSLFTable table) {
        List<List<String>> rows = new ArrayList<>();
        int rowCount = table.getNumberOfRows();
        int columnCount = table.getNumberOfColumns();
        for (int r = 0; r < rowCount; r++) {
            List<String> cells = new ArrayList<>();
            for (int c = 0; c < columnCount; c++) {
                HSLFTableCell cell = table.getCell(r, c);
                cells.add(cell == null ? "" : cell.getText());
            }
            rows.add(cells);
        }
        return rows;
    }

    private Integer headingLevelFromStyle(String style) {
        if (!StringUtils.hasText(style)) {
            return null;
        }
        Matcher matcher = Pattern.compile("(?i)heading\\s*([1-6])|标题\\s*([1-6])").matcher(style);
        if (!matcher.find()) {
            return null;
        }
        String value = matcher.group(1) == null ? matcher.group(2) : matcher.group(1);
        return Integer.parseInt(value);
    }

    private Integer inferHeadingLevel(String line) {
        if (line.length() > 80 || line.endsWith(".") || line.endsWith(",") || line.endsWith(";")) {
            return null;
        }
        if (NUMBERED_HEADING.matcher(line).matches()) {
            long dots = line.chars().filter(ch -> ch == '.').count();
            return Math.min(6, Math.max(2, (int) dots + 2));
        }
        if (line.length() <= 30 && !line.contains("。") && !line.contains("，") && !line.contains(",")) {
            return 3;
        }
        return null;
    }

    private String normalizeText(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        StringBuilder markdown = new StringBuilder();
        String[] lines = text.replace("\r", "\n").split("\\n");
        for (String line : lines) {
            appendLineAsMarkdownBlock(markdown, line);
        }
        return markdown.toString();
    }

    private String normalizeInline(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\r", "\n").replaceAll("[ \\t\\x0B\\f]+", " ").trim();
    }

    private String normalizeTableCell(String value) {
        return normalizeInline(value).replace("|", "\\|").replace("\n", "<br>");
    }

    private String resolveFileType(String originalFilename) {
        if (!StringUtils.hasText(originalFilename) || !originalFilename.contains(".")) {
            return "";
        }
        return originalFilename.substring(originalFilename.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
    }

    private String repeat(String value, int times) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < times; i++) {
            sb.append(value);
        }
        return sb.toString();
    }
}
