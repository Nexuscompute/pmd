/**
 * BSD-style license; for more info see http://pmd.sourceforge.net/license.html
 */

package net.sourceforge.pmd.cpd;

import java.io.IOException;
import java.io.Writer;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import net.sourceforge.pmd.PMDVersion;
import net.sourceforge.pmd.lang.document.Chars;
import net.sourceforge.pmd.lang.document.FileId;
import net.sourceforge.pmd.lang.document.FileLocation;
import net.sourceforge.pmd.reporting.Report;
import net.sourceforge.pmd.util.StringUtil;

/**
 * @author Philippe T'Seyen - original implementation
 * @author Romain Pelisse - javax.xml implementation
 *
 */
public final class XMLRenderer implements CPDReportRenderer {
    private static final String NAMESPACE_URI = "https://pmd-code.org/schema/cpd-report";
    private static final String NAMESPACE_LOCATION = "https://pmd.github.io/schema/cpd-report_1_0_0.xsd";
    private static final String SCHEMA_VERSION = "1.0.0";

    private String encoding;

    /**
     * Creates a XML Renderer with the default (platform dependent) encoding.
     */
    public XMLRenderer() {
        this(null);
    }

    /**
     * Creates a XML Renderer with a specific output encoding.
     *
     * @param encoding
     *            the encoding to use or null. If null, default (platform
     *            dependent) encoding is used.
     */
    public XMLRenderer(String encoding) {
        setEncoding(encoding);
    }

    public void setEncoding(String encoding) {
        if (encoding != null) {
            this.encoding = encoding;
        } else {
            this.encoding = System.getProperty("file.encoding");
        }
    }

    public String getEncoding() {
        return this.encoding;
    }

    private Document createDocument() {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder parser = factory.newDocumentBuilder();
            return parser.newDocument();
        } catch (ParserConfigurationException e) {
            throw new IllegalStateException(e);
        }
    }

    private void dumpDocToWriter(Document doc, Writer writer) {
        try {
            TransformerFactory tf = TransformerFactory.newInstance();
            Transformer transformer = tf.newTransformer();
            transformer.setOutputProperty(OutputKeys.VERSION, "1.0");
            transformer.setOutputProperty(OutputKeys.METHOD, "xml");
            transformer.setOutputProperty(OutputKeys.ENCODING, encoding);
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty(OutputKeys.CDATA_SECTION_ELEMENTS, "{" + NAMESPACE_URI + "}codefragment");
            transformer.transform(new DOMSource(doc), new StreamResult(writer));
        } catch (TransformerException e) {
            throw new IllegalStateException(e);
        }
    }


    @Override
    public void render(final CPDReport report, final Writer writer) throws IOException {
        final Document doc = createDocument();
        final Element root = doc.createElementNS(NAMESPACE_URI, "pmd-cpd");
        root.setAttributeNS(XMLConstants.W3C_XML_SCHEMA_INSTANCE_NS_URI, "xsi:schemaLocation", NAMESPACE_URI + " " + NAMESPACE_LOCATION);

        root.setAttributeNS(NAMESPACE_URI, "version", SCHEMA_VERSION);
        root.setAttributeNS(NAMESPACE_URI, "pmdVersion", PMDVersion.VERSION);
        root.setAttributeNS(NAMESPACE_URI, "timestamp", OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        final Map<FileId, Integer> numberOfTokensPerFile = report.getNumberOfTokensPerFile();
        doc.appendChild(root);

        for (final Map.Entry<FileId, Integer> pair : numberOfTokensPerFile.entrySet()) {
            final Element fileElement = doc.createElementNS(NAMESPACE_URI, "file");
            fileElement.setAttributeNS(NAMESPACE_URI, "path", report.getDisplayName(pair.getKey()));
            fileElement.setAttributeNS(NAMESPACE_URI, "totalNumberOfTokens", String.valueOf(pair.getValue()));
            root.appendChild(fileElement);
        }

        for (Match match : report.getMatches()) {
            Element dupElt = createDuplicationElement(doc, match);
            addFilesToDuplicationElement(doc, dupElt, match, report);
            addCodeSnippet(doc, dupElt, match, report);
            root.appendChild(dupElt);
        }

        for (Report.ProcessingError error : report.getProcessingErrors()) {
            Element errorElt = doc.createElementNS(NAMESPACE_URI, "error");
            errorElt.setAttributeNS(NAMESPACE_URI, "filename", report.getDisplayName(error.getFileId()));
            errorElt.setAttributeNS(NAMESPACE_URI, "msg", error.getMsg());
            errorElt.setTextContent(error.getDetail());
            root.appendChild(errorElt);
        }

        dumpDocToWriter(doc, writer);
        writer.flush();
    }

    private void addFilesToDuplicationElement(Document doc, Element duplication, Match match, CPDReport report) {
        for (Mark mark : match) {
            final Element file = doc.createElementNS(NAMESPACE_URI, "file");
            FileLocation loc = mark.getLocation();
            file.setAttributeNS(NAMESPACE_URI, "line", String.valueOf(loc.getStartLine()));
            // only remove invalid characters, escaping is done by the DOM impl.
            String filenameXml10 = StringUtil.removedInvalidXml10Characters(report.getDisplayName(loc.getFileId()));
            file.setAttributeNS(NAMESPACE_URI, "path", filenameXml10);
            file.setAttributeNS(NAMESPACE_URI, "endline", String.valueOf(loc.getEndLine()));
            file.setAttributeNS(NAMESPACE_URI, "column", String.valueOf(loc.getStartColumn()));
            file.setAttributeNS(NAMESPACE_URI, "endcolumn", String.valueOf(loc.getEndColumn()));
            file.setAttributeNS(NAMESPACE_URI, "begintoken", String.valueOf(mark.getBeginTokenIndex()));
            file.setAttributeNS(NAMESPACE_URI, "endtoken", String.valueOf(mark.getEndTokenIndex()));
            duplication.appendChild(file);
        }
    }

    private void addCodeSnippet(Document doc, Element duplication, Match match, CPDReport report) {
        Chars codeSnippet = report.getSourceCodeSlice(match.getFirstMark());
        if (codeSnippet != null) {
            // the code snippet has normalized line endings
            String platformSpecific = codeSnippet.toString().replace("\n", System.lineSeparator());
            Element codefragment = doc.createElementNS(NAMESPACE_URI, "codefragment");
            // only remove invalid characters, escaping is not necessary in CDATA.
            // if the string contains the end marker of a CDATA section, then the DOM impl will
            // create two cdata sections automatically.
            codefragment.appendChild(doc.createCDATASection(StringUtil.removedInvalidXml10Characters(platformSpecific)));
            duplication.appendChild(codefragment);
        }
    }

    private Element createDuplicationElement(Document doc, Match match) {
        Element duplication = doc.createElementNS(NAMESPACE_URI, "duplication");
        duplication.setAttributeNS(NAMESPACE_URI, "lines", String.valueOf(match.getLineCount()));
        duplication.setAttributeNS(NAMESPACE_URI, "tokens", String.valueOf(match.getTokenCount()));
        return duplication;
    }
}
