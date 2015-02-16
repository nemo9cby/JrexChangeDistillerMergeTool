package com.merge;

import java.io.File;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import ch.uzh.ifi.seal.changedistiller.ChangeDistiller;
import ch.uzh.ifi.seal.changedistiller.ChangeDistiller.Language;
import ch.uzh.ifi.seal.changedistiller.distilling.FileDistiller;
import ch.uzh.ifi.seal.changedistiller.model.entities.SourceCodeChange;

public class RevisionComparator {
	
	private static final int BIGINTEGER = 99999;
	
	private Element preRevisionElem;
	private Element curRevisionElem;
	
	private String preSrc;
	private String curSrc;
	
	private String currentDir;
	private String fileName;
	
	private HashMap<String, Element> preMethodMap = new HashMap<String, Element>();
	private HashMap<String, Element> curMethodMap = new HashMap<String, Element>();
	private HashMap<String, ArrayList<String>> preCommentMap = new HashMap<String, ArrayList<String>>();
	private HashMap<String, ArrayList<String>> curCommentMap = new HashMap<String, ArrayList<String>>();
	private HashMap<String, Element> updateMethodMap = new HashMap<String, Element>();
	private HashMap<String, Integer> preImportMap = new HashMap<String, Integer>();
	private HashMap<String, Integer> curImportMap = new HashMap<String, Integer>();

	
	
	private Document preDoc;
	private Document curDoc;
	private Document outputDoc;
	private Element Evolution;
	
	private int lineAdded = 0;
	private int lineDeleted = 0;
	private int lineUpdated = 0;
	private int lineMoved = 0;
	
	private HashMap<String, int[]> codeChurnMap = new HashMap<String, int[]>();	
	
	
	RevisionComparator(Element pre, Element cur, String path, String fName) throws Exception
	{
		preRevisionElem = pre;
		curRevisionElem = cur;
		currentDir = path.substring(0, path.lastIndexOf("/"));
		fileName = fName;
		
		DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
		DocumentBuilder dbBuilder = dbFactory.newDocumentBuilder();
		NodeList preList = preRevisionElem.getElementsByTagName("AST");
		Element preASTElem = (Element)preList.item(0);		
		NodeList curList = curRevisionElem.getElementsByTagName("AST");
		Element curASTElem = (Element)curList.item(0);
		preDoc = dbBuilder.newDocument();
		curDoc = dbBuilder.newDocument();
		preDoc.appendChild(preDoc.importNode(preASTElem, true));
		curDoc.appendChild(curDoc.importNode(curASTElem, true));
		
		loadSourceCode();
		loadMethod();
		loadComment(preDoc, preCommentMap);
		loadComment(curDoc, curCommentMap);
		loadImport(preASTElem, preImportMap);
		loadImport(curASTElem, curImportMap);
	}
	
	public Document compare(Document doc, Element elem)
	{
		outputDoc = doc;
		Evolution = outputDoc.createElement("Evolution");
		elem.appendChild(Evolution);
		Evolution.setAttribute("preVersion", preRevisionElem.getAttribute("versionNumber"));
		Evolution.setAttribute("curVersion", curRevisionElem.getAttribute("versionNumber"));
		Evolution.setAttribute("curTime", curRevisionElem.getAttribute("date"));
		Evolution.setAttribute("author", curRevisionElem.getAttribute("author"));
		Evolution.setAttribute("churn", curRevisionElem.getAttribute("churn"));
		Evolution.setAttribute("logm", curRevisionElem.getAttribute("logm"));
		Evolution.setAttribute("logmadd", curRevisionElem.getAttribute("logmadd"));
		Evolution.setAttribute("logmdel", curRevisionElem.getAttribute("logmdel"));
		Evolution.setAttribute("state", curRevisionElem.getAttribute("state"));
		Evolution.setAttribute("text", curRevisionElem.getAttribute("text"));
		Evolution.setAttribute("type", curRevisionElem.getAttribute("type"));
		cdAddCompare();
		cdCompare();
		//logReport();
		return outputDoc;
	}
	
	private void logReport()
	{
		NodeList modifyList = Evolution.getElementsByTagName("modify");
		for (int i = 0; i < modifyList.getLength(); i ++)
		{
			Element modifyElem = (Element) modifyList.item(i);
			NodeList nl = modifyElem.getChildNodes();
			for (int j = 0; j < nl.getLength(); j ++)
			{
				if (nl.item(j).getNodeType() == Node.ELEMENT_NODE)
				{
					Element elem = (Element) nl.item(j);
					String content = elem.getAttribute("content");
					String regex = "(.)*(log|logger|trace|system\\.out|system\\.err)(.)*";
					Pattern p = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
					Matcher m = p.matcher(content);
					if (m.find())
					{
						Element log = outputDoc.createElement("Log");
						log.setAttribute("content", content);
						log.setAttribute("method", elem.getAttribute("parent"));
						log.setAttribute("operation", elem.getNodeName());
						Evolution.appendChild(log);
					}
				}
			}
			
		}
	}
	
	private void cdCompare()
	{
		String preFileName = preRevisionElem.getAttribute("versionNumber") + "|||||" + fileName;
		String curFileName = curRevisionElem.getAttribute("versionNumber") + "|||||" + fileName;

		File left = new File(currentDir + "/" + preFileName);
		File right = new File(currentDir + "/" + curFileName);
		FileDistiller distiller = ChangeDistiller.createFileDistiller(Language.JAVA);
		ChangeDistiller.getProvidedLanguages();
		try 
		{
			distiller.extractClassifiedSourceCodeChanges(left, right);
		} catch(Exception e) {
			System.err.println(e.getMessage());
		}
		List<SourceCodeChange> changes = distiller.getSourceCodeChanges();
		for (SourceCodeChange change : changes)
		{
			String[] changeOperation = change.toString().split(":");
			String[] changeMethod = change.getChangedEntity().getUniqueName().split("\\.");	
			String[] rootName = change.getRootEntity().getUniqueName().split("\\.");
			
			if (change.getRootEntity().getType().isClass() && change.getChangedEntity().getType().isMethod())
			{
				
				if (changeOperation[0].equals("Insert"))
				{
					String keyOfInsertMethod = rootName[rootName.length - 1] + "|||||" + changeMethod[changeMethod.length - 1];
					if (curMethodMap.get(keyOfInsertMethod) == null)
					{
						System.out.printf("Compare between %s and %s, the insert method: %s is not found in jrex. \n"
								,preFileName, curFileName, keyOfInsertMethod);
						break;
					}
					Element InsertMethodNode = (Element) curMethodMap.get(keyOfInsertMethod);
					Element insertNode = outputDoc.createElement("Insert");
					insertNode.setAttribute("type", "Method");
					insertNode.setAttribute("parent", rootName[rootName.length - 1]);
					Evolution.appendChild(insertNode);
					Node importInsertMethodNode = outputDoc.importNode(InsertMethodNode, true);						
					insertNode.appendChild(importInsertMethodNode);
					
					// 计算整个方法在源代码中有多少行
					//System.out.printf("Method keyword is %s", keyOfInsertMethod);
					String searchMethodKeyword = getRegex(InsertMethodNode);
					int startOftheMethod = keyLocation(searchMethodKeyword, curSrc);
					
					if ( startOftheMethod == -1)
					{
						System.out.println("ERROR!!! " + keyOfInsertMethod + "NOT found in sourcecode " + curFileName);
						continue;
					}
					/* calculate method line */
					String after = curSrc.substring(startOftheMethod, curSrc.length() - 1);
					int methodLineNumber = methodLine(after);
					if (methodLineNumber == -1)
					{
						System.out.println("ERROR!!! " + keyOfInsertMethod + " LINE NUMBER IS WRONG!!!");
					}
					Element codeChurnElem = outputDoc.createElement("CodeChurn");
					//codeChurnElem.setAttribute("added", methodStringArray.length + "");
					codeChurnElem.setAttribute("added", methodLineNumber + "");
					insertNode.appendChild(codeChurnElem);
					lineAdded += methodLineNumber;
				}
				if (changeOperation[0].equals("Delete"))
				{
					String keyOfDeleteMethod = rootName[rootName.length - 1] + "|||||" + changeMethod[changeMethod.length - 1];
					if (preMethodMap.get(keyOfDeleteMethod) == null)
					{
						System.out.printf("Compare between %s and %s, the delete method: %s is not found in jrex. \n"
								,preFileName, curFileName, keyOfDeleteMethod);
						break;
					}
					// import the method node from jrex AST
					Element DeleteMethodNode = (Element) preMethodMap.get(keyOfDeleteMethod);
					Element deleteNode = outputDoc.createElement("Delete");
					deleteNode.setAttribute("type", "Method");
					deleteNode.setAttribute("parent", rootName[rootName.length - 1]);
					Evolution.appendChild(deleteNode);
					Node importDeleteMethodNode = outputDoc.importNode(DeleteMethodNode, true);	
					deleteNode.appendChild(importDeleteMethodNode);
					
					// calculate churn
					String searchMethodKeyword = getRegex(DeleteMethodNode);
					int startOftheMethod = keyLocation(searchMethodKeyword, preSrc);
					if (startOftheMethod == -1)
					{
						System.out.println("ERROR!!! " + keyOfDeleteMethod + "NOT found in sourcecode " + preFileName);
						continue;
					}				
					String after = preSrc.substring(startOftheMethod, preSrc.length() - 1);
					int methodLineNumber = methodLine(after);
					if (methodLineNumber == -1)
					{
						System.out.println("ERROR!!! " + keyOfDeleteMethod + " LINE NUMBER IS WRONG!!!");
					}
				
					Element codeChurnElem = outputDoc.createElement("CodeChurn");
					codeChurnElem.setAttribute("deleted", methodLineNumber + "");
					deleteNode.appendChild(codeChurnElem);
					lineDeleted = lineDeleted + methodLineNumber;
				}	
			}
			else 
			{
				String changeMethodKey = "";
				if (rootName.length > 1)
					changeMethodKey = rootName[rootName.length - 2] + "|||||" + rootName[rootName.length - 1];
				else 
					changeMethodKey = rootName[rootName.length - 1];
				
				if (change.getChangedEntity().getType().isComment())
					continue;
				// Insert
				if (changeOperation[0].equals("Insert"))
				{
					Element methodElem;
					if (change.getChangedEntity().getType().isField())
					{
						methodElem = outputDoc.createElement("Insert");
						methodElem.setAttribute("content", change.getChangedEntity().getUniqueName());
						methodElem.setAttribute("parent", change.getRootEntity().getUniqueName());
						methodElem.setAttribute("type", "Field");
						Evolution.appendChild(methodElem);
						lineAdded ++;
						continue;
					}
					if (updateMethodMap.get(changeMethodKey) != null)
					{
						methodElem = updateMethodMap.get(changeMethodKey);
					}
					else {				
						methodElem = outputDoc.createElement("modify");
						methodElem.setAttribute("type", "Method");
						methodElem.setAttribute("name", change.getRootEntity().getUniqueName());
						Evolution.appendChild(methodElem);
						updateMethodMap.put(changeMethodKey, methodElem);
					}
					
					Element insertNode = outputDoc.createElement("Insert");
					insertNode.setAttribute("type", change.getChangedEntity().getType().toString());
					insertNode.setAttribute("content", change.getChangedEntity().getUniqueName());
					insertNode.setAttribute("parent", change.getRootEntity().getUniqueName());
					methodElem.appendChild(insertNode);

					
					if (codeChurnMap.get(changeMethodKey) == null){
						int[] lineEdit = {0, 0, 0, 0};
						lineEdit[0] ++;
						codeChurnMap.put(changeMethodKey, lineEdit);
					}
					else{
						int[] tmp = codeChurnMap.get(changeMethodKey);
						tmp[0] ++;
					}
					lineAdded ++;
					
				}
				// Delete
				if(changeOperation[0].equals("Delete")){
					Element methodElem;
					if (change.getChangedEntity().getType().isField())
					{
						System.out.println(change);
						methodElem = outputDoc.createElement("Delete");
						methodElem.setAttribute("content", change.getChangedEntity().getUniqueName());
						methodElem.setAttribute("type", "Field");
						Evolution.appendChild(methodElem);
					    lineDeleted ++;
						continue;
					}
					if (updateMethodMap.get(changeMethodKey) != null)
					{
						methodElem = updateMethodMap.get(changeMethodKey);
					}
					else {	
						methodElem = outputDoc.createElement("modify");
						methodElem.setAttribute("type", "Method");
						methodElem.setAttribute("name", change.getRootEntity().getUniqueName());
						Evolution.appendChild(methodElem);
						updateMethodMap.put(changeMethodKey, methodElem);
					}
					Element deleteNode = outputDoc.createElement("Delete");
					deleteNode.setAttribute("type", change.getChangedEntity().getType().toString());
					deleteNode.setAttribute("content", change.getChangedEntity().getUniqueName());
					deleteNode.setAttribute("parent", change.getRootEntity().getUniqueName());
					methodElem.appendChild(deleteNode);
					
					if (codeChurnMap.get(changeMethodKey) == null){
						int[] lineEdit = {0, 0, 0, 0};
						lineEdit[1] ++;
						codeChurnMap.put(changeMethodKey, lineEdit);
					}
					else{
						int[] tmp = codeChurnMap.get(changeMethodKey);
						tmp[1] ++;
					}
					lineDeleted ++;
				}
				// Update
				if (changeOperation[0].equals("Update"))
				{
					Element methodElem;
					if (change.getChangedEntity().getType().isField())
					{
						methodElem = outputDoc.createElement("Update");
						methodElem.setAttribute("content", change.getChangedEntity().getUniqueName());
						methodElem.setAttribute("type", "Field");
						Evolution.appendChild(methodElem);
						lineUpdated ++;
						continue;
					}
					if (updateMethodMap.get(changeMethodKey) != null)
						methodElem = updateMethodMap.get(changeMethodKey);
					else 
					{
						methodElem = outputDoc.createElement("modify");
						methodElem.setAttribute("type", "Method");
						methodElem.setAttribute("name", change.getRootEntity().getUniqueName());
						Evolution.appendChild(methodElem);
						updateMethodMap.put(changeMethodKey, methodElem);
					}
					Element updateNode = outputDoc.createElement("Update");
					updateNode.setAttribute("type", change.getChangedEntity().getType().toString());
					updateNode.setAttribute("content", change.getChangedEntity().getUniqueName());
					updateNode.setAttribute("parent", change.getRootEntity().getUniqueName());
					methodElem.appendChild(updateNode);		
					
					if (codeChurnMap.get(changeMethodKey) == null){
						int[] lineEdit = {0, 0, 0, 0};
						lineEdit[2] ++;
						codeChurnMap.put(changeMethodKey, lineEdit);
					}
					else{
						int[] tmp = codeChurnMap.get(changeMethodKey);
						tmp[2] ++;
					}
					lineUpdated ++;
					
				}
				if (changeOperation[0].equals("Move"))
				{
					Element methodElem;
					if (change.getChangedEntity().getType().isField())
					{
						System.out.println(change);
						methodElem = outputDoc.createElement("Move");
						methodElem.setAttribute("content", change.getChangedEntity().getUniqueName());
						methodElem.setAttribute("type", "Field");
						Evolution.appendChild(methodElem);
						lineMoved ++;
						continue;
					}
					if(updateMethodMap.get(changeMethodKey) != null)
						methodElem = updateMethodMap.get(changeMethodKey);
					else 
					{
						methodElem = outputDoc.createElement("modify");
						//methodElem.setAttribute("level", "Class");
						methodElem.setAttribute("type", "Method");
						methodElem.setAttribute("name", change.getRootEntity().getUniqueName());
						Evolution.appendChild(methodElem);
						updateMethodMap.put(changeMethodKey, methodElem);
					}
					Element moveNode = outputDoc.createElement("Move");
					moveNode.setAttribute("type", change.getChangedEntity().getType().toString());
					moveNode.setAttribute("content", change.getChangedEntity().getUniqueName());
					moveNode.setAttribute("parent", change.getRootEntity().getUniqueName());
					methodElem.appendChild(moveNode);
					
						
					if (codeChurnMap.get(changeMethodKey) == null){
						int[] lineEdit = {0, 0, 0, 0};
						lineEdit[3] ++;
						codeChurnMap.put(changeMethodKey, lineEdit);
					}
					else{
						int[] tmp = codeChurnMap.get(changeMethodKey);
						tmp[3] ++;
					}
					lineMoved ++;
					
				}
			}
			
		}
		
		for (Map.Entry<String, Element> entry : updateMethodMap.entrySet())
		{
			String key = entry.getKey();
			Element methodElem = updateMethodMap.get(key);
			int[] a = codeChurnMap.get(key);
			Element codeChurn = outputDoc.createElement("CodeChurn");
			codeChurn.setAttribute("insert", a[0] + "");
			codeChurn.setAttribute("delete", a[1] + "");
			codeChurn.setAttribute("update", a[2] + "");
			codeChurn.setAttribute("move", a[3] + "");
			methodElem.appendChild(codeChurn);
		}
		
		Element codeChurn = outputDoc.createElement("TotalCodeChurn");
		codeChurn.setAttribute("insert", lineAdded + "");
		codeChurn.setAttribute("delete", lineDeleted + "");
		codeChurn.setAttribute("update", lineUpdated + "");
		codeChurn.setAttribute("move", lineMoved + "");
		Evolution.appendChild(codeChurn);
	}
	
	private int methodLine (String src)
	{
		int countOfBracket = 0;
		int lineNumber = 0;
		for (int i = 0; i < src.length(); i ++)
		{
			char c = src.charAt(i);
			if (c == '\n')
				lineNumber ++;
			if (c == '{')
				countOfBracket ++;
			if (c == '}')
			{
				countOfBracket --;
				if (countOfBracket  == 0)
					return lineNumber;
			}
		}
		if ( lineNumber <= 0)
			return -1;
		return lineNumber;
	}
	
	private int keyLocation (String regex, String input)
	{
		Pattern p = Pattern.compile(regex, Pattern.DOTALL);
		Matcher m = p.matcher(input);
		if (m.find())
		{
			return (m.start());
		}
		else{
			//System.out.println(regex);
			//System.out.println(input);
			return -1;
		}
	}
	
	private String getRegex (Element methodNode)
	{
		String searchMethodKeyword = methodNode.getAttribute("name") + "(\\s*?)" + "\\(";
		NodeList paraList = methodNode.getElementsByTagName("parameter");
		String paraStr = "";
		// 组成 method 的关键字 （应当组成正则表达式）
		for (int i = 0; i < paraList.getLength(); i ++ )
		{
			Element paraElem = (Element) paraList.item(i);
			// type [] issue
			
			String typeStr = paraElem.getAttribute("type");
			String typeRegex;
			if (i == 0)
			{
				typeRegex = "\\s*?";		// 这里应该还会有修改
			}
			else
			{
				typeRegex = ".*?";
			}
			// 若含有特殊字符，则加反斜杠 \
				for(int j = 0; j < typeStr.length(); j ++)
				{
					if (Character.isLetter(typeStr.charAt(j)) || Character.isDigit(typeStr.charAt(j))) 
					{
						typeRegex = typeRegex.concat(typeStr.charAt(j) + "");
					}
					else 
					{
						if (typeStr.charAt(j) == ' ')
							typeRegex = typeRegex.concat("(\\s*?)");
						else{
							typeRegex = typeRegex.concat("(\\s*?)");
							typeRegex = typeRegex.concat("\\" + typeStr.charAt(j) );
						}
					}
				}
			
			//typeRegex = typeRegex.concat(".*?");
			searchMethodKeyword = searchMethodKeyword.concat(typeRegex);
			//
			/*
			paraStr = paraStr.concat(typeRegex + "(\\s*?)" + paraElem.getAttribute("name"));	
			if(i != paraList.getLength() - 1)
				paraStr = paraStr.concat("(\\,)(\\s*?)");
			*/
		}
		searchMethodKeyword = searchMethodKeyword.concat(paraStr) + ".*?\\)";
		System.out.printf("regex is %s\n", searchMethodKeyword);
		return searchMethodKeyword;
	}
	
	private void cdAddCompare()
	{
		// import
		for (Map.Entry<String, Integer> m : preImportMap.entrySet())
		{
			String key = m.getKey();
			// delete
			if (!curImportMap.containsKey(key))
			{
				Element nElem = outputDoc.createElement("Insert");
				nElem.setAttribute("content", key);
				nElem.setAttribute("type", "import");
				Evolution.appendChild(nElem);
				lineAdded ++;
			}
		}
		for (Map.Entry<String, Integer> m : curImportMap.entrySet())
		{
			String key = m.getKey();
			// delete
			if (!preImportMap.containsKey(key))
			{
				Element nElem = outputDoc.createElement("Delete");
				nElem.setAttribute("content", key);
				nElem.setAttribute("type", "import");
				Evolution.appendChild(nElem);
				lineDeleted ++;
			}
		}
		// comment
		for (Map.Entry<String, ArrayList<String>> m : preCommentMap.entrySet())
		{
			String key = m.getKey();
			String[] genre = key.split("\\|\\|\\|\\|\\|");
			if (curCommentMap.get(key) != null)
			{
				ArrayList<String> preCommentList = preCommentMap.get(key);
				ArrayList<String> curCommentList = curCommentMap.get(key);
				for (int i = 0; i < curCommentList.size();)
				{
					int shortestDistance = BIGINTEGER;
					int pairNum = -1;
					for (int j = 0; j < preCommentList.size(); j ++)
					{
						if (computeLevenshteinDistance(curCommentList.get(i), preCommentList.get(j)) < shortestDistance)
						{
							shortestDistance = computeLevenshteinDistance(curCommentList.get(i), preCommentList.get(j));
							pairNum = j;
						}			
					}
					// the two comments are the same
					if (shortestDistance == 0)
					{
						curCommentList.remove(i);
						preCommentList.remove(pairNum);
						continue;
					}
					// the two comments are similar
					else if (shortestDistance < 0.1 * curCommentList.get(i).length())
					{
						Element nElem = outputDoc.createElement("Update");			
						nElem.setAttribute("type", "Comment");
						nElem.setAttribute("preText", preCommentList.get(pairNum));
						nElem.setAttribute("curText", curCommentList.get(i));
						
						if (genre[0].equals("Class")  || genre[0].equals("File"))
						{
							nElem.setAttribute("parent", genre[1]);
							Evolution.appendChild(nElem);
							lineUpdated ++;
						}
						else
						{
							Element updateMethodElem;
							if (updateMethodMap.get(key) != null)
							{
								updateMethodElem = updateMethodMap.get(key);
							}
							else 
							{
								updateMethodElem = outputDoc.createElement("modify");
								updateMethodElem.setAttribute("type", "Method");
								updateMethodElem.setAttribute("name",genre[1]);
								Evolution.appendChild(updateMethodElem);
								updateMethodMap.put(key, updateMethodElem);
							}
							updateMethodElem.appendChild(nElem);
							
							if (codeChurnMap.get(key) == null){
								int[] lineEdit = {0, 0, 0, 0};
								lineEdit[2] ++;
								codeChurnMap.put(key, lineEdit);
							}
							else{
								int[] tmp = codeChurnMap.get(key);
								tmp[2] ++;
							}
							lineUpdated ++;
						}
						curCommentList.remove(i);
						preCommentList.remove(pairNum);
					}
					// the two comments are totally different
					else {
						Element nElem = outputDoc.createElement("Insert");
						nElem.setAttribute("type", "Comment");
						nElem.setAttribute("curText", curCommentList.get(i));
						if (genre[0].equals("Class")  || genre[0].equals("File"))
						{
							nElem.setAttribute("parent", genre[1]);
							Evolution.appendChild(nElem);
							lineAdded ++;
						}
						else
						{
							Element updateMethodElem;
							if (updateMethodMap.get(key) != null)
							{
								updateMethodElem = updateMethodMap.get(key);
							}
							else 
							{
								updateMethodElem = outputDoc.createElement("modify");
								updateMethodElem.setAttribute("type", "Method");
								updateMethodElem.setAttribute("name",genre[1]);
								Evolution.appendChild(updateMethodElem);
								updateMethodMap.put(key, updateMethodElem);
							}
							updateMethodElem.appendChild(nElem);
							
							if (codeChurnMap.get(key) == null){
								int[] lineEdit = {0, 0, 0, 0};
								lineEdit[0] ++;
								codeChurnMap.put(key, lineEdit);
							}
							else{
								int[] tmp = codeChurnMap.get(key);
								tmp[0] ++;
							}
							lineAdded ++;
						}
						curCommentList.remove(i);
					}
				}
				for (int i = 0; i < preCommentList.size();)
				{
					Element nElem = outputDoc.createElement("Delete");
					nElem.setAttribute("type", "Comment");
					nElem.setAttribute("preText", preCommentList.get(i));
					if (genre[0].equals("Class")  || genre[0].equals("File"))
					{
						nElem.setAttribute("parent", genre[1]);
						Evolution.appendChild(nElem);
						lineDeleted ++;
					}
					else
					{
						Element updateMethodElem;
						if (updateMethodMap.get(key) != null)
						{
							updateMethodElem = updateMethodMap.get(key);
							
						}
						else 
						{
							updateMethodElem = outputDoc.createElement("modify");
							updateMethodElem.setAttribute("type", "Method");
							updateMethodElem.setAttribute("name",genre[1]);
							Evolution.appendChild(updateMethodElem);
							updateMethodMap.put(key, updateMethodElem);
						}
						updateMethodElem.appendChild(nElem);
						
						if (codeChurnMap.get(key) == null){
							int[] lineEdit = {0, 0, 0, 0};
							lineEdit[1] ++;
							codeChurnMap.put(key, lineEdit);
						}
						else{
							int[] tmp = codeChurnMap.get(key);
							tmp[1] ++;
						}
						lineDeleted ++;
					}
					preCommentList.remove(i);
				}
			}
		}
	}

	private void loadImport(Element elem, HashMap<String, Integer> map) throws Exception
	{
		NodeList importList = elem.getElementsByTagName("import");
		for (int i = 0; i < importList.getLength(); i ++)
		{
			Element nElem = (Element) importList.item(i);
			String content = nElem.getAttribute("name");
			map.put(content, 1);
		}
	}
	
	private void loadComment(Document doc, HashMap<String, ArrayList<String>> map) throws Exception 
	{
		NodeList commentList =  doc.getElementsByTagName("comment");
		for (int i = 0; i < commentList.getLength(); i ++)
		{
			Element nElem = (Element)commentList.item(i);
			Element pElem = nElem;
			do {
				 pElem = (Element)pElem.getParentNode();
			} while (!pElem.getNodeName().equals("Method") && !pElem.getNodeName().equals("Class") && !pElem.getNodeName().equals("File"));
			String key = "";
			if (pElem.getNodeName().equals("Method"))
			{
				key = getMethodString(pElem);
			}
			else if (pElem.getNodeName().equals("Class"))
			{
				key = "Class" + "|||||" + pElem.getAttribute("name");
			}
			else if (pElem.getNodeName().equals("File"))
			{
				key = "File" + "|||||" + "File";
			}
			if (map.get(key) != null)
			{
				ArrayList<String> arr = map.get(key);
				arr.add(nElem.getTextContent());
			}
			else 
			{
				ArrayList<String> arr = new ArrayList<String>();
				arr.add(nElem.getTextContent());
				map.put(key, arr);
			}		
		}	
	}
	
	private void loadMethod() throws Exception 
	{
		NodeList preMethodList = preDoc.getElementsByTagName("Method");
		NodeList curMethodList = curDoc.getElementsByTagName("Method");
		for (int i = 0; i < preMethodList.getLength(); i++) 
		{
			Element mElem = (Element) preMethodList.item(i);
			String key = getMethodString(mElem);
			preMethodMap.put(key, mElem);
		}
		for (int i = 0; i < curMethodList.getLength(); i++) 
		{
			Element mElem = (Element) curMethodList.item(i);
			String key = getMethodString(mElem);
			curMethodMap.put(key, mElem);
		}
	}
	
	private String getMethodString(Element elem)
	{
		String parameterStr = "";
		NodeList parameterNL = elem.getElementsByTagName("parameter");
		for (int n = 0; n < parameterNL.getLength(); n ++)
		{
			Element parameterElem = (Element) parameterNL.item(n);
			parameterStr = parameterStr.concat(parameterElem.getAttribute("type"));
			if(n != parameterNL.getLength() - 1)
				parameterStr = parameterStr.concat(",");
		}
		Element classElem = (Element) elem.getParentNode();
		String methodKey = classElem.getAttribute("name") + "|||||" + elem.getAttribute("name")+ "(" +parameterStr + ")";
		
		return methodKey;
	}
	
	private void loadSourceCode() throws Exception
	{	
		NodeList preList = preRevisionElem.getElementsByTagName("sourceCode");
		Element preSourceCodeElem = (Element)preList.item(0);
		preSrc = preSourceCodeElem.getTextContent();
		
		NodeList curList = curRevisionElem.getElementsByTagName("sourceCode");
		Element curSourceCodeElem = (Element)curList.item(0);
		curSrc = curSourceCodeElem.getTextContent();		
		 
	}
	public static int computeLevenshteinDistance(String str1,String str2) {      
        int[][] distance = new int[str1.length() + 1][str2.length() + 1];        
 
        for (int i = 0; i <= str1.length(); i++)                                 
            distance[i][0] = i;                                                  
        for (int j = 1; j <= str2.length(); j++)                                 
            distance[0][j] = j;                                                  
 
        for (int i = 1; i <= str1.length(); i++)                                 
            for (int j = 1; j <= str2.length(); j++)                             
                distance[i][j] = minimum(                                        
                        distance[i - 1][j] + 1,                                  
                        distance[i][j - 1] + 1,                                  
                        distance[i - 1][j - 1] + ((str1.charAt(i - 1) == str2.charAt(j - 1)) ? 0 : 1));
 
        return distance[str1.length()][str2.length()];                           
    }   
	private static int minimum(int a, int b, int c) {                            
        return Math.min(Math.min(a, b), c);                                      
    }  
}
