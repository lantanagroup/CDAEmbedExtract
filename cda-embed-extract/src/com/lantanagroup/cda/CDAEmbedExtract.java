package com.lantanagroup.cda;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URLConnection;
import java.util.Base64;
import java.util.Base64.Decoder;
import java.util.Base64.Encoder;
import java.util.UUID;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.DOMException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

public class CDAEmbedExtract {
	
	private static File in;
	private static File out;
	private static File inputFolder;
	private static File outputFolder;
	private Document cdaDoc;
	private boolean extract = true;
	DocumentBuilder db;

	public static void main(String[] args) throws ParserConfigurationException, SAXException, IOException, DOMException, TransformerException {
		String sourceCDAPath = args[0];
		String destCDAPath = args[1];
		String direction = "extract";
		if (args.length > 2){
			direction = args[2];
			System.out.println(direction);
		}
		in = new File(sourceCDAPath);
		out = new File(destCDAPath);
		outputFolder = out.getParentFile();
		inputFolder = in.getParentFile();
		CDAEmbedExtract cee = new CDAEmbedExtract();
		if(direction.equals("embed")){
			cee.extract = false;
		} 
		System.out.println("extract="+cee.extract);
		cee.run();
	}
	
	public CDAEmbedExtract() throws ParserConfigurationException, SAXException, IOException, DOMException, TransformerException{
		db = DocumentBuilderFactory.newInstance().newDocumentBuilder();
		cdaDoc = db.parse(in);
	}
	
	public void run() throws DOMException, IOException, TransformerException{
		if (extract){
			extractCDA();
		} else {
			embedCDA();
		}
	}
	
	private void extractCDA() throws DOMException, IOException, TransformerException{
		System.out.println("Starting extractCDA");
		NodeList nl = cdaDoc.getElementsByTagName("text");
		for(int i = 0 ; i < nl.getLength() ; i++){
			Element text = (Element) nl.item(i);
			System.out.println("Found node " + text.getTagName());
			extractB64FromElement(text);
		}

		nl = cdaDoc.getElementsByTagName("observationMedia");
		for(int i = 0 ; i < nl.getLength() ; i++){
			Element om = (Element) nl.item(i);
			System.out.println("Found node " + om.getTagName());
			NodeList valueList = om.getElementsByTagName("value");
			for (int x = 0; x < valueList.getLength() ; x++ ){
				Element value = (Element) valueList.item(x);
				extractB64FromElement(value);
			}
		}
		writeDocumentOutput();
		System.out.println("Extraced base64 content from CDA");
	}

	private void extractB64FromElement(Element elem) throws IOException {
		if (elem.hasAttribute("representation") && elem.getAttribute("representation").equals("B64") && elem.hasAttribute("mediaType")){
			File f = extractBase64File(elem.getTextContent(), elem.getAttribute("mediaType"));
			Element reference = cdaDoc.createElementNS("urn:hl7-org:v3", "reference");
			reference.setAttribute("value", f.getName());
			removeChildren(elem);
			elem.appendChild(reference);
			elem.removeAttribute("representation");
		}
	}
	
	private void embedCDA() throws TransformerException, IOException{
		System.out.println("Starting embedCDA");
		NodeList nl = cdaDoc.getElementsByTagName("text");
		for(int i = 0 ; i < nl.getLength() ; i++){
			Element text = (Element) nl.item(i);
			System.out.println("Found node " + text.getTagName());
			embedB64InElement(text);
		}
		nl = cdaDoc.getElementsByTagName("observationMedia");
		for(int i = 0 ; i < nl.getLength() ; i++){
			Element om = (Element) nl.item(i);
			System.out.println("Found node " + om.getTagName());
			NodeList valueList = om.getElementsByTagName("value");
			for (int x = 0; x < valueList.getLength() ; x++ ){
				Element value = (Element) valueList.item(x);
				embedB64InElement(value);
			}
		}
		writeDocumentOutput();
		System.out.println("Embedded base64 content into CDA");
	}

	private void embedB64InElement(Element elem) throws IOException {
		Element ref = getReference(elem);
		if (ref != null){
			File f = new File(inputFolder, ref.getAttribute("value"));
			String b64 = encodeFile(f);
			removeChildren(elem);
			elem.setTextContent(b64);
			elem.setAttribute("representation", "B64");
			String mediaType = URLConnection.guessContentTypeFromName(f.getName());
			elem.setAttribute("mediaType", mediaType);
		}
	}
	
	private String encodeFile(File f) throws IOException {
		Encoder e = Base64.getEncoder();
		FileInputStream fis = new FileInputStream(f);
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		for (int i = fis.read() ; i != -1 ; i = fis.read()){
			baos.write(i);
		}
		fis.close();
		baos.close();
		return e.encodeToString(baos.toByteArray());
	}

	private Element getReference(Element e) {
		Element ref = null;
		NodeList nl = e.getElementsByTagName("reference");
		if (nl.getLength() > 0){
			ref = (Element) nl.item(0);
		}
		return ref;
	}

	private void removeChildren(Element elem) {
		NodeList nl = elem.getChildNodes();
		for (int i = 0; i < nl.getLength() ; i++){
			Node n = nl.item(i);
			elem.removeChild(n);
		}
	}

	private void writeDocumentOutput() throws FileNotFoundException, TransformerException {
	    TransformerFactory tFactory =
	    TransformerFactory.newInstance();
	    Transformer transformer = 
	    tFactory.newTransformer();
	    DOMSource source = new DOMSource(cdaDoc);
	    FileOutputStream fos = new FileOutputStream(out);
	    StreamResult result = new StreamResult(fos);
	    transformer.transform(source, result);
	}

	private File extractBase64File (String b64, String mediaType) throws IOException{
		Decoder d = Base64.getDecoder();
		byte[] decodedBytes = d.decode(b64);
		UUID uuidStr = UUID.randomUUID();
		String extension = mediaTypeToFileExtension(mediaType);
		String fileName = uuidStr.toString() + extension;
		System.out.println("Saving file as " + fileName);
		File f = new File(outputFolder,fileName);
		FileOutputStream fos = new FileOutputStream(f);
		fos.write(decodedBytes);;
		fos.close();
		return f;
	}
	
	private String mediaTypeToFileExtension(String mediaType){
		System.out.println(mediaType);
		String fe = ".file";
		//TODO: Add support for all the media types supported by the attachments guide. 
		switch(mediaType){
		case "application/pdf":
			fe = ".pdf";
			break;
		case "image/gif":
			fe = ".gif";
			break;
		case "image/jepg":
			fe = ".jpg";
			break;
		case "image/tiff":
			fe = ".tif";
			break;
		case "text/plain":
			fe = ".text";
			break;
		}
		return fe;
	}

}
