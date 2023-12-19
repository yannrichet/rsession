/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.math.R;

import java.util.ArrayList;
import java.util.List;

/**
 * 
 * @author Nicolas Chabalier
 */
public class R2jsUtils {
    
    /**
     * Parse an expression containing multiple lines, functions or sub-expressions
     * in a list of inline sub-expressions. This function also cleanup comments.
     * 
     * @param expr expression to parse
     * @return a list of inline sub-expressions
     */
    public static List<String> parse(String expr) {
        
        List<String> expressions = new ArrayList<>();
        
        int parenthesis = 0; // '(' and ')'
        int brackets = 0; // '{' and '}'
        int brackets2 = 0; // '[' and ']'

        String[] lines = expr.split("\n");
        StringBuilder sb = new StringBuilder();
        for(String line : lines) {
            line = line.trim();
            if(line.startsWith("#")) {
                // Ignore commented lines
            } else {
                char currentChar=0;
                for (int i = 0; i < line.length(); i++) {
                    currentChar = line.charAt(i);
                    if (currentChar == '#') {// Ignore rest of line
                        line = line.substring(0,i);
                        break; 
                    } else if (currentChar == '(') {
                        parenthesis++;
                    } else if (currentChar == ')') {
                        parenthesis--;
                    } else if (currentChar == '{') {
                        brackets++;
                    } else if (currentChar == '}') {
                        brackets--;
                    } else if (currentChar == '[') {
                        brackets2++;
                    } else if (currentChar == ']') {
                        brackets2--;
                    } else if (parenthesis == 0 && brackets == 0 && brackets2 == 0) {
                        if(currentChar == ';') {
                            String firstLine = line.substring(0, i+1);
                            sb.append(firstLine);                            
                            expressions.add(sb.toString());
                            sb = new StringBuilder();
                                                        
                            String remainder = line.substring(i+1);
                            line = remainder.trim()+"\n";
                            i=0;
                        }
                    }
                }
                if(line.trim().length()>0) {
                    sb.append(line);
                    if(parenthesis == 0 && brackets == 0 && brackets2 == 0) {
                        expressions.add(sb.toString());
                        sb = new StringBuilder();
                    } else {
                        if (currentChar!=',' && currentChar!='+' && currentChar!='-' && currentChar!='*' &&currentChar!='/' &&currentChar!='=')
                        sb.append(";\n");
                    }
                }
            }
        }
        
        return expressions;
    }
    
    
}
