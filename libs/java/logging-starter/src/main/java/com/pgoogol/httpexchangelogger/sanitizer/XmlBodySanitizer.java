package com.pgoogol.httpexchangelogger.sanitizer;

import com.pgoogol.httpexchangelogger.autoconfigure.HttpExchangeLoggerProperties;
import com.pgoogol.httpexchangelogger.support.ContentTypeMatcher;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.Objects;
import java.util.stream.IntStream;

public class XmlBodySanitizer implements BodySanitizer {

    public static final String UNPARSEABLE_PLACEHOLDER = "[not logged: body could not be parsed for masking]";

    private final SensitiveValueMasker masker;
    private final boolean maskingEnabled;
    private final boolean hasSensitiveFields;
    private final DocumentBuilderFactory documentBuilderFactory;
    private final TransformerFactory transformerFactory;

    public XmlBodySanitizer(SensitiveValueMasker masker, HttpExchangeLoggerProperties.Mask maskProperties) {

        this.masker = masker;
        this.maskingEnabled = Objects.nonNull(maskProperties) && maskProperties.isEnabled();
        this.hasSensitiveFields = Objects.nonNull(maskProperties)
                && !CollectionUtils.isEmpty(maskProperties.getFields());
        this.documentBuilderFactory = createHardenedBuilderFactory();
        this.transformerFactory = createHardenedTransformerFactory();
    }

    private static DocumentBuilderFactory createHardenedBuilderFactory() {

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        trySetFeature(factory, "http://apache.org/xml/features/disallow-doctype-decl", true);
        trySetFeature(factory, "http://xml.org/sax/features/external-general-entities", false);
        trySetFeature(factory, "http://xml.org/sax/features/external-parameter-entities", false);
        trySetFeature(factory, "http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        return factory;
    }

    private static void trySetFeature(DocumentBuilderFactory factory, String feature, boolean value) {

        try {

            factory.setFeature(feature, value);
        } catch (Exception ignored) {

            // Feature unsupported by current parser; other XXE controls still apply.
        }
    }

    private static TransformerFactory createHardenedTransformerFactory() {

        TransformerFactory factory = TransformerFactory.newInstance();
        try {

            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
        } catch (Exception ignored) {

            // Attribute unsupported by current transformer; safe to continue.
        }
        return factory;
    }

    @Override
    public String sanitize(String body, String contentType) {

        if (!StringUtils.hasLength(body)) {

            return body;
        }
        if (!maskingEnabled) {

            return body;
        }
        if (!ContentTypeMatcher.isXml(contentType)) {

            return body;
        }

        try {

            DocumentBuilder builder = documentBuilderFactory.newDocumentBuilder();
            Document document = builder.parse(new InputSource(new StringReader(body)));
            maskElement(document.getDocumentElement());
            return serialize(document);
        } catch (Exception ex) {

            // Fail closed: masking was requested but body could not be parsed. Never echo raw.
            if (hasSensitiveFields) {

                return UNPARSEABLE_PLACEHOLDER;
            }
            return body;
        }
    }

    private void maskElement(Element element) {

        if (Objects.isNull(element)) {

            return;
        }
        maskAttributes(element);
        if (masker.isSensitive(localName(element))) {

            removeAllChildren(element);
            element.setTextContent(SensitiveValueMasker.MASK);
            return;
        }
        NodeList children = element.getChildNodes();
        IntStream.range(0, children.getLength())
                .mapToObj(children::item)
                .filter(node -> node.getNodeType() == Node.ELEMENT_NODE)
                .map(Element.class::cast)
                .forEach(this::maskElement);
    }

    private void maskAttributes(Element element) {

        NamedNodeMap attributes = element.getAttributes();
        if (Objects.isNull(attributes)) {

            return;
        }
        IntStream.range(0, attributes.getLength())
                .mapToObj(attributes::item)
                .filter(Attr.class::isInstance)
                .map(Attr.class::cast)
                .filter(attr -> masker.isSensitive(localName(attr)))
                .forEach(attr -> attr.setValue(SensitiveValueMasker.MASK));
    }

    private String localName(Node node) {

        String local = node.getLocalName();
        return Objects.nonNull(local) ? local : node.getNodeName();
    }

    private void removeAllChildren(Element element) {

        while (element.hasChildNodes()) {

            element.removeChild(element.getFirstChild());
        }
    }

    private String serialize(Document document) throws Exception {

        Transformer transformer = transformerFactory.newTransformer();
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
        transformer.setOutputProperty(OutputKeys.INDENT, "no");
        StringWriter writer = new StringWriter();
        transformer.transform(new DOMSource(document), new StreamResult(writer));
        return writer.toString();
    }

}
