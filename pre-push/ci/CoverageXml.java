import java.nio.file.Files;
import java.nio.file.Path;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;
import java.io.StringReader;

/** Infrastructure-only XML reader. Never resolves DTDs, entities, schemas or URLs. */
public class CoverageXml {
    private static Element parse(Path path) throws Exception {
        String xml = Files.readString(path);
        // JaCoCo emits this fixed declaration; no other DOCTYPE is accepted.
        xml = xml.replace("<!DOCTYPE report PUBLIC \"-//JACOCO//DTD Report 1.1//EN\" \"report.dtd\">", "");
        if (xml.contains("<!DOCTYPE") || xml.contains("<!ENTITY")) throw new Exception("DTD refused");
        var factory = DocumentBuilderFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        var builder = factory.newDocumentBuilder();
        builder.setErrorHandler(new org.xml.sax.helpers.DefaultHandler() {
            @Override public void error(org.xml.sax.SAXParseException e) throws org.xml.sax.SAXException { throw e; }
            @Override public void fatalError(org.xml.sax.SAXParseException e) throws org.xml.sax.SAXException { throw e; }
        });
        return builder.parse(new InputSource(new StringReader(xml))).getDocumentElement();
    }
    private static String q(String value) throws Exception {
        if (!value.matches("[A-Za-z0-9_.$/ -]*")) throw new Exception("Unsupported XML identifier");
        return "\"" + value + "\"";
    }
    private static long number(Element element, String key) throws Exception {
        String text = element.getAttribute(key);
        if (!text.matches("[0-9]{1,12}")) throw new Exception("Invalid counter");
        return Long.parseLong(text);
    }
    private static String counters(Element parent) throws Exception {
        String result = null;
        boolean executable = parent.getElementsByTagName("line").getLength() > 0;
        for (Node n = parent.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n instanceof Element e && e.getTagName().equals("counter")) {
                number(e, "covered"); number(e, "missed");
                if ((e.getAttribute("type").equals("INSTRUCTION") || e.getAttribute("type").equals("BRANCH"))
                    && number(e, "covered") + number(e, "missed") > 0) executable = true;
                if (e.getAttribute("type").equals("LINE")) {
                    if (result != null) throw new Exception("Duplicate LINE counter");
                    result = "{\"covered\":" + number(e,"covered") + ",\"missed\":" + number(e,"missed") + "}";
                }
            }
        }
        // JaCoCo emits sourcefiles for interfaces/constants/generated-only code
        // without LINE counters. Keep them in the source/class inventory as 0/0;
        // never grant this treatment to executable sources or the report root.
        if (result == null && parent.getTagName().equals("sourcefile") && !executable) {
            return "{\"covered\":0,\"missed\":0}";
        }
        if (result == null) throw new Exception("LINE counter missing");
        return result;
    }
    public static void main(String[] args) {
        try {
            Element report = parse(Path.of(args[0]));
            if (!report.getTagName().equals("report")) throw new Exception("Expected report");
            StringBuilder sources = new StringBuilder(), classes = new StringBuilder();
            var packages = report.getElementsByTagName("package");
            for (int p = 0; p < packages.getLength(); p++) {
                Element pkg = (Element) packages.item(p);
                for (Node n = pkg.getFirstChild(); n != null; n = n.getNextSibling()) {
                    if (!(n instanceof Element e)) continue;
                    if (e.getTagName().equals("class")) {
                        if (!classes.isEmpty()) classes.append(',');
                        classes.append("{\"name\":").append(q(e.getAttribute("name")))
                            .append(",\"source\":").append(q(e.getAttribute("sourcefilename"))).append('}');
                    }
                    if (!e.getTagName().equals("sourcefile")) continue;
                    if (!sources.isEmpty()) sources.append(',');
                    sources.append("{\"path\":").append(q(pkg.getAttribute("name") + "/" + e.getAttribute("name")))
                        .append(",\"counter\":").append(counters(e)).append(",\"lines\":[");
                    var lines = e.getElementsByTagName("line");
                    for (int i = 0; i < lines.getLength(); i++) {
                        if (i > 0) sources.append(',');
                        Element line = (Element) lines.item(i);
                        sources.append("{\"nr\":").append(number(line,"nr"));
                        for (String key : new String[]{"mi","ci","mb","cb"}) {
                            sources.append(",\"").append(key).append("\":").append(number(line,key));
                        }
                        sources.append('}');
                    }
                    sources.append("]}");
                }
            }
            long tests = 0, skipped = 0, errors = 0, failures = 0;
            try (var files = Files.list(Path.of(args[1]))) {
                for (Path file : files.filter(f -> f.getFileName().toString().matches("TEST-.*\\.xml")).toList()) {
                    Element suite = parse(file);
                    if (!suite.getTagName().equals("testsuite")) throw new Exception("Expected testsuite");
                    if (suite.getElementsByTagName("testcase").getLength() != number(suite,"tests")
                        || suite.getElementsByTagName("skipped").getLength() != number(suite,"skipped")
                        || suite.getElementsByTagName("failure").getLength() != number(suite,"failures")
                        || suite.getElementsByTagName("error").getLength() != number(suite,"errors")) throw new Exception("Test counters disagree");
                    tests += number(suite,"tests"); skipped += number(suite,"skipped");
                    failures += number(suite,"failures"); errors += number(suite,"errors");
                }
            }
            System.out.println("{\"name\":" + q(report.getAttribute("name")) + ",\"counter\":" + counters(report)
                + ",\"sources\":[" + sources + "],\"classes\":[" + classes + "],\"tests\":" + tests
                + ",\"skipped\":" + skipped + ",\"errors\":" + errors + ",\"failures\":" + failures + "}");
        } catch (Exception e) {
            System.err.println("Coverage XML/test metadata invalid; raw parser output withheld");
            System.exit(1);
        }
    }
}
