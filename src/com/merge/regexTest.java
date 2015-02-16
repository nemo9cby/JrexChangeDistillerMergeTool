package com.merge;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class regexTest {
	public static void main (String[] args)
	{
		String regex = "conf.*?String";
		String input = "protected static Job createJob(Configuration conf, \nString jobName,";
		System.out.println(input);
		Pattern p = Pattern.compile(regex, Pattern.DOTALL);
		Matcher m = p.matcher(input);
		if (m.find())
		{
			System.out.println(m.start());
		}

	}
}
