package kr.itsdev.devjobcollector.collection.adapter;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

/** Parses the documented Work24 list response; it does not publish collected jobs. */
final class Work24ResponseParser {
    record Job(String id, String company, String title, String region, String career,
               String registeredDate, String closingDate, String url, String occupationCode) {}
    record Page(int total, int page, List<Job> jobs) {}

    Page parse(String xml) {
        try {
            var factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            var builder = factory.newDocumentBuilder();
            builder.setErrorHandler(new org.xml.sax.helpers.DefaultHandler() {
                @Override public void fatalError(org.xml.sax.SAXParseException e) throws org.xml.sax.SAXException { throw e; }
            });
            Element root = builder.parse(new InputSource(new StringReader(xml))).getDocumentElement();
            if (!"wantedRoot".equals(root.getTagName())) throw new IllegalArgumentException("Unexpected Work24 response root");
            int total = integer(required(root, "total"));
            int page = integer(required(root, "startPage"));
            if (total < 0 || page < 1 || page > 1000) throw new IllegalArgumentException("Invalid Work24 pagination");
            List<Job> jobs = new ArrayList<>();
            for (Node node = root.getFirstChild(); node != null; node = node.getNextSibling()) {
                if (!(node instanceof Element item) || !"wanted".equals(item.getTagName())) continue;
                jobs.add(new Job(required(item, "wantedAuthNo"), optional(item, "company"), required(item, "title"),
                        optional(item, "region"), optional(item, "career"), optional(item, "regDt"),
                        optional(item, "closeDt"), required(item, "wantedInfoUrl"), optional(item, "jobsCd")));
            }
            if (jobs.size() > total || (page == 1 && total > 0 && jobs.isEmpty())) {
                throw new IllegalArgumentException("Inconsistent Work24 page");
            }
            return new Page(total, page, List.copyOf(jobs));
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid Work24 XML", e);
        }
    }

    private static int integer(String value) {
        try { return Integer.parseInt(value); }
        catch (NumberFormatException e) { throw new IllegalArgumentException("Invalid Work24 number", e); }
    }

    private static String required(Element parent, String name) {
        String value = optional(parent, name);
        if (value == null) throw new IllegalArgumentException("Missing Work24 field: " + name);
        return value;
    }

    private static String optional(Element parent, String name) {
        for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node instanceof Element element && name.equals(element.getTagName())) {
                String value = element.getTextContent().trim();
                return value.isEmpty() ? null : value;
            }
        }
        return null;
    }
}
