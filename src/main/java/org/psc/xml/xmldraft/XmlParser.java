package org.psc.xml.xmldraft;

import java.io.IOException;
import java.io.StringWriter;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import com.wds.claimrecordation.erv.external.model.GBAntragTyp;

public class XmlParser {
	private static final Logger LOGGER = LoggerFactory.getLogger(XmlParser.class);

	public static void main(String[] args)
			throws ParserConfigurationException, SAXException, IOException, TransformerException {
		XmlParser parser = new XmlParser();
		parser.parseXml();
		Map<String, String> namespaces = XmlElementReflector.getNamespaceMapping(GBAntragTyp.class);
		LOGGER.info(namespaces.get("GB_AntragTyp"));
	}

	public void parseXml() throws ParserConfigurationException, SAXException, IOException, TransformerException {
		DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
		DocumentBuilder docBuilder = docFactory.newDocumentBuilder();

		Document document = docBuilder.parse(getClass().getResourceAsStream("/sample.xml"));

		LOGGER.info(document.getDocumentElement().getTagName());
		Element originalElem = document.getDocumentElement();
		LOGGER.info(document.getDocumentElement().getTextContent());
		Element newElem = document.createElementNS("http://test-ns", originalElem.getNodeName());
		newElem.setPrefix("xx");
		NodeList nodes = originalElem.getChildNodes();
		while (nodes.getLength() != 0) {
			newElem.appendChild(nodes.item(0));
		}
		document.replaceChild(newElem, originalElem);

		String text = document.getDocumentElement().getTextContent();
		LOGGER.info(documentToString(document));
	}

	private String documentToString(Document document) throws TransformerException {
		TransformerFactory transfac = TransformerFactory.newInstance();
		Transformer trans = transfac.newTransformer();
		trans.setOutputProperty(OutputKeys.METHOD, "xml");
		trans.setOutputProperty(OutputKeys.INDENT, "yes");
		trans.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", Integer.toString(2));

		StringWriter sw = new StringWriter();
		StreamResult result = new StreamResult(sw);
		DOMSource source = new DOMSource(document.getDocumentElement());

		trans.transform(source, result);
		return sw.toString();
	}
}
