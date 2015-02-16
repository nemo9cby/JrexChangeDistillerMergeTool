package com.merge;

import java.io.File;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class LogReport {
	public static void main (String[] args)	throws Exception
	{
		DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
		DocumentBuilder dbBuilder = dbFactory.newDocumentBuilder();
		Document logDoc = dbBuilder.newDocument();
		Element logElem = logDoc.createElement("Log");
		logDoc.appendChild(logElem);
		logElem = log(new File("/Users/Nemo/Desktop/incubator2"), logElem, logDoc);
		
		TransformerFactory transformerFactory = TransformerFactory.newInstance();
		transformerFactory.setAttribute("indent-number", 2);
		Transformer transformer = transformerFactory.newTransformer();
		transformer.setOutputProperty(OutputKeys.INDENT, "yes");
		DOMSource source = new DOMSource(logDoc);
		StreamResult result = new StreamResult(new File("/Users/Nemo/Desktop/incubator2/logreport"));					
		transformer.transform(source, result);
	}
	
	public static Element log(File node, Element out, Document outputDoc) throws Exception
	{
		if (node.isFile() && node.getName().contains(".output"))
		{
			boolean haveLog = false;
			DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
			DocumentBuilder dbBuilder = dbFactory.newDocumentBuilder();
			Document doc = dbBuilder.parse(node);
			NodeList fList = doc.getElementsByTagName("File");
			
			Element fileElem = (Element)fList.item(0);
			String fileName = fileElem.getAttribute("name");
			NodeList list = doc.getElementsByTagName("Evolution");
			Element newFileElem = outputDoc.createElement("File");
			newFileElem.setAttribute("File", fileName);
			
			for (int k = 0 ; k < list.getLength(); k ++)
			{
				Element Evolution = (Element)list.item(k);
				String verNum = Evolution.getAttribute("curVersion");
				Element rev = outputDoc.createElement("Revision");
				rev.setAttribute("version", verNum);
				NodeList modifyList = Evolution.getElementsByTagName("modify");
				NodeList List = Evolution.getElementsByTagName("Insert"); //这里不对，应该是level=method
				//NodeList deleteList = Evolution.getElementsByTagName("Delete");
				for (int i = 0; i < List.getLength(); i ++)
				{
					Element mElem = (Element) List.item(i);
					String type = mElem.getAttribute("type");
					if (type.equals("Method"))
					{
						NodeList n = mElem.getElementsByTagName("Method");
						Element methodstrElem = (Element) n.item(0);
						String methodString = methodstrElem.getAttribute("modifier") +" " + methodstrElem.getAttribute("returntype") + " " + methodstrElem.getAttribute("name");
						Document tmpDoc = dbBuilder.newDocument();
						Element a = (Element) tmpDoc.importNode(mElem, true);
						tmpDoc.appendChild(a);
						NodeList nl = tmpDoc.getElementsByTagName("invoke");
						for (int j = 0; j < nl.getLength(); j ++)
						{
							Element e = (Element) nl.item(j);
							String test = "caller:" + e.getAttribute("caller") + " " + "callertype:" + " " + e.getAttribute("callertype");						
							test = test.toLowerCase();
							if (test.contains("log") ||
									test.contains("logger") ||
									test.contains("system.out") ||
									test.contains("system.err") ||
									test.contains("trace"))
							{
								if(haveLog)
								{
									out.appendChild(rev);
									Element log = outputDoc.createElement("LogEntity");
									log.setAttribute("content", test);
									log.setAttribute("method", methodString);
									log.setAttribute("operation", "Insert");
									log.setAttribute("insideMethod", "yes");
									rev.appendChild(log);
								}
								else
								{
									out.appendChild(newFileElem);
									newFileElem.appendChild(rev);
									Element log = outputDoc.createElement("LogEntity");
									log.setAttribute("content", test);
									log.setAttribute("method", methodString);
									log.setAttribute("operation", "Insert");
									log.setAttribute("insideMethod", "yes");
									rev.appendChild(log);
									haveLog = true;
								}
							}
						}
					}
				}
				List = Evolution.getElementsByTagName("Delete");
				
				for (int i = 0; i < List.getLength(); i ++)
				{
					Element mElem = (Element) List.item(i);
					String type = mElem.getAttribute("type");
					if (type.equals("Method"))
					{
						NodeList n = mElem.getElementsByTagName("Method");
						Element methodstrElem = (Element)n.item(0);
						String methodString = methodstrElem.getAttribute("modifier") +" " + methodstrElem.getAttribute("returntype") + " " + methodstrElem.getAttribute("name");
						Document tmpDoc = dbBuilder.newDocument();
						Element a = (Element) tmpDoc.importNode(mElem, true);
						tmpDoc.appendChild(a);
						NodeList nl = tmpDoc.getElementsByTagName("invoke");
						for (int j = 0; j < nl.getLength(); j ++)
						{
							Element e = (Element) nl.item(j);
							String test = "caller:" + e.getAttribute("caller") + " " + "callertype:" + " " + e.getAttribute("callertype");
							test = test.toLowerCase();
							if (test.contains("log") ||
									test.contains("logger") ||
									test.contains("system.out") ||
									test.contains("system.err") ||
									test.contains("trace"))
							{
								if(haveLog)
								{
									out.appendChild(rev);
									Element log = outputDoc.createElement("LogEntity");
									log.setAttribute("content", test);
									log.setAttribute("method", methodString);
									log.setAttribute("operation", "Delete");
									log.setAttribute("insideMethod", "yes");
									rev.appendChild(log);
								}
								else
								{
									out.appendChild(newFileElem);
									newFileElem.appendChild(rev);
									Element log = outputDoc.createElement("LogEntity");
									log.setAttribute("content", test);
									log.setAttribute("method", methodString);
									log.setAttribute("operation", "Delete");
									log.setAttribute("insideMethod", "yes");
									rev.appendChild(log);
									haveLog = true;
								}
							}
						}
					}
				}
				for (int i = 0; i < modifyList.getLength(); i ++)
				{
					Element modifyElem = (Element) modifyList.item(i);
					NodeList nl = modifyElem.getChildNodes();
					for (int j = 0; j < nl.getLength(); j ++)
					{
						if (nl.item(j).getNodeType() == Node.ELEMENT_NODE)
						{
							Element elem = (Element) nl.item(j);
							String type = elem.getAttribute("type");
							type = type.toLowerCase();
							if (type.contains("comment") || type.contains("javadoc"))
								continue;
							else{
							String content = elem.getAttribute("content");
							String regex = "(.)*(log|logger|trace|system\\.out|system\\.err)(.)*";
							Pattern p = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
							Matcher m = p.matcher(content);
							if (m.find())
							{
								if(haveLog)
								{
								out.appendChild(rev);
								Element log = outputDoc.createElement("LogEntity");
								log.setAttribute("content", content);
								log.setAttribute("method", elem.getAttribute("parent"));
								log.setAttribute("operation", elem.getNodeName());
								rev.appendChild(log);
								
								}
								else
								{
									out.appendChild(newFileElem);
									newFileElem.appendChild(rev);
									Element log = outputDoc.createElement("LogEntity");
									log.setAttribute("content", content);
									log.setAttribute("method", elem.getAttribute("parent"));
									log.setAttribute("operation", elem.getNodeName());
									rev.appendChild(log);
									haveLog = true;
								}
							}
							}
						}
					}
					
				}
			}
			return out;
		}
		else if (node.isDirectory()) 
		{
			String[] subNode = node.list();
			for(String filename: subNode)
			{		
				out = log(new File(node, filename), out, outputDoc);
			}
		}
		return out;
	}
}
