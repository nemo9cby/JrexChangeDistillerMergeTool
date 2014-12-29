package com.merge;


import java.io.BufferedInputStream;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.util.Scanner;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public class Merge {
	
	public static final boolean FIRST = false;
	
	public static void main (String[] args) throws Exception
	{
		//recordEvorex(new File("/Users/cbb/Desktop/incubator2"));
		//readFromJournal(new File("/Users/cbb/Desktop/journal"));
		initializeEvorex(new File("/Users/cbb/Desktop/incubator2"));
		
	}
	
	public static void readFromJournal (File node) throws Exception {
		//Scanner inFinish = new Scanner(node);
		int finishLine = countLines(node.getAbsolutePath());
		Scanner inTodo = new Scanner(new File("/Users/cbb/Desktop/todolist"));
		int n = 0;
		while(inTodo.hasNextLine())
		{
			if( n < finishLine){
				n ++;
				inTodo.nextLine();
				continue;
			}
			File nNode = new File(inTodo.nextLine());
			DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
			DocumentBuilder dbBuilder = dbFactory.newDocumentBuilder();
			Document doc = dbBuilder.parse(nNode);
			Document outputDoc = dbBuilder.newDocument();
			Element outputFileElem = outputDoc.createElement("File");
			outputFileElem.setAttribute("name", nNode.getName());
			outputDoc.appendChild(outputFileElem);
			NodeList revisionList = doc.getElementsByTagName("revision");
			String currentDir = node.getAbsolutePath().substring(0, node.getAbsolutePath().lastIndexOf("/"));

			for (int i = 0; i + 1 < revisionList.getLength(); i ++)
			{
				Element left = (Element) revisionList.item(i);
				Element right = (Element) revisionList.item(i + 1);
				RevisionComparator r = new RevisionComparator(left, right, node.getAbsolutePath(),node.getName().substring(0, node.getName().lastIndexOf(".")));
				outputDoc = r.compare(outputDoc, outputFileElem);
			}
			TransformerFactory transformerFactory = TransformerFactory.newInstance();
			transformerFactory.setAttribute("indent-number", 2);
			Transformer transformer = transformerFactory.newTransformer();
			transformer.setOutputProperty(OutputKeys.INDENT, "yes");
			DOMSource source = new DOMSource(outputDoc);
			StreamResult result = new StreamResult(new File(currentDir + "/" + node.getName().substring(0, node.getName().lastIndexOf(".")) + ".output"));					
			transformer.transform(source, result);
			
			// journal
			try(PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter("/Users/cbb/Desktop/journal", true))))
			{
				out.println(node.getAbsolutePath());
			}

		}
		inTodo.close();
	}
	
	public static void initializeEvorex(File node) throws Exception {
		if (node.isFile() && node.getName().contains(".evorex")) 
		{
			System.out.println(node.getName());
			DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
			DocumentBuilder dbBuilder = dbFactory.newDocumentBuilder();
			Document doc = dbBuilder.parse(node);
			Document outputDoc = dbBuilder.newDocument();
			Element outputFileElem = outputDoc.createElement("File");
			outputFileElem.setAttribute("name", node.getName());
			// outputFileElem.setAttribute("path", filePath);
			outputDoc.appendChild(outputFileElem);
			
			NodeList revisionList = doc.getElementsByTagName("revision");
			NodeList srcList = doc.getElementsByTagName("sourceCode");
			String currentDir = node.getAbsolutePath().substring(0, node.getAbsolutePath().lastIndexOf("/"));
			String nfileName ="";
			if (FIRST) {
			for (int i = 0; i < srcList.getLength(); i ++)
			{
				Element revision = (Element) srcList.item(i).getParentNode();
				Element srcElem = (Element) srcList.item(i);
				String content = srcElem.getTextContent();
				nfileName = revision.getAttribute("versionNumber") + "|||||" + node.getName().substring(0, node.getName().lastIndexOf("."));;
				try (PrintWriter out = new PrintWriter(currentDir+"/"+nfileName))
				{
					out.println(content);
				}	
			}
			}
			
			for (int i = 0; i + 1 < revisionList.getLength(); i ++)
			{
				Element left = (Element) revisionList.item(i);
				Element right = (Element) revisionList.item(i + 1);
				RevisionComparator r = new RevisionComparator(left, right, node.getAbsolutePath(),node.getName().substring(0, node.getName().lastIndexOf(".")));
				outputDoc = r.compare(outputDoc, outputFileElem);
			}
			
			TransformerFactory transformerFactory = TransformerFactory.newInstance();
			transformerFactory.setAttribute("indent-number", 2);
			Transformer transformer = transformerFactory.newTransformer();
			transformer.setOutputProperty(OutputKeys.INDENT, "yes");
			DOMSource source = new DOMSource(outputDoc);
			StreamResult result = new StreamResult(new File(currentDir + "/" + node.getName().substring(0, node.getName().lastIndexOf(".")) + ".output"));					
			transformer.transform(source, result);
			
			// journal
			try(PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter("/Users/cbb/Desktop/journal", true))))
			{
				out.println(node.getAbsolutePath());
			}
		}
		else if (node.isDirectory()) 
		{
			String[] subNode = node.list();
			for(String filename: subNode)
			{		
				initializeEvorex(new File(node, filename));
			}
		}
	}
	
	
	public static void recordEvorex (File node) throws Exception{
		if (node.isFile() && node.getName().contains(".evorex")) 
		{
			record(node);		
		}
		
		if (node.isDirectory()) 
		{
			String[] subNode = node.list();
			for(String filename: subNode)
			{		
				recordEvorex(new File(node, filename));
			}
		}
	}
	public static void record (File file) throws Exception
	{
		String evorexFileAbsolutePath = file.getAbsolutePath(); 
		try(PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter("/Users/cbb/Desktop/todolist", true))))
		{
			out.println(evorexFileAbsolutePath);
		} catch (IOException e) {
			e.printStackTrace();
		}				
	}
	public static int countLines(String filename) throws IOException {
	    InputStream is = new BufferedInputStream(new FileInputStream(filename));
	    try {
	        byte[] c = new byte[1024];
	        int count = 0;
	        int readChars = 0;
	        boolean empty = true;
	        while ((readChars = is.read(c)) != -1) {
	            empty = false;
	            for (int i = 0; i < readChars; ++i) {
	                if (c[i] == '\n') {
	                    ++count;
	                }
	            }
	        }
	        return (count == 0 && !empty) ? 1 : count;
	    } finally {
	        is.close();
	    }
	}
}
