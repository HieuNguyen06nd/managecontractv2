package com.hieunguyen.ManageContract.common.constants;

import org.docx4j.Docx4J;
import org.docx4j.convert.out.HTMLSettings;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

public class DocxToHtmlConverter {

    private DocxToHtmlConverter() {
        // Utility class, không cho khởi tạo
    }

    public static String convertToHtml(WordprocessingMLPackage wordMLPackage) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();

            //  Tạo HTMLSettings đúng cách
            HTMLSettings htmlSettings = new HTMLSettings();
            htmlSettings.setWmlPackage(wordMLPackage);

            //  Convert DOCX -> HTML
            Docx4J.toHTML(htmlSettings, out, Docx4J.FLAG_EXPORT_PREFER_XSL);

            return out.toString(StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Error converting DOCX to HTML: " + e.getMessage(), e);
        }
    }
}