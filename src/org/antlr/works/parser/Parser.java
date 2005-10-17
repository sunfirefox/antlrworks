/*

[The "BSD licence"]
Copyright (c) 2005 Jean Bovet
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions
are met:

1. Redistributions of source code must retain the above copyright
notice, this list of conditions and the following disclaimer.
2. Redistributions in binary form must reproduce the above copyright
notice, this list of conditions and the following disclaimer in the
documentation and/or other materials provided with the distribution.
3. The name of the author may not be used to endorse or promote products
derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY EXPRESS OR
IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT,
INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF
THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

*/

package org.antlr.works.parser;

import org.antlr.works.visualization.grammar.GrammarEngineError;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class Parser {

    protected List tokens;
    protected int position;

    protected Token currentToken;

    public static final String ATTRIBUTE_FRAGMENT = "fragment";
    public static final String BEGIN_GROUP = "// $<";
    public static final String END_GROUP = "// $>";

    public static final List blockIdentifiers;
    public static final List keywords;

    protected Lexer lexer;

    public List rules = null;
    public List groups = null;
    public List blocks = null;
    public Name name = null;

    static {
        blockIdentifiers = new ArrayList();
        blockIdentifiers.add("options");
        blockIdentifiers.add("tokens");
        blockIdentifiers.add("header");

        keywords = new ArrayList();
        keywords.add("options");
        keywords.add("tokens");
        keywords.add("header");
        keywords.add("grammar");
        keywords.add("fragment");
    }

    public Parser() {
    }

    public void parse(String text) {
        rules = new ArrayList();
        groups = new ArrayList();
        blocks = new ArrayList();

        lexer = new Lexer(text);
        tokens = lexer.parseTokens();
        position = -1;
        while(nextToken()) {
            Name n = matchName();
            if(n != null) {
                name = n;
                continue;
            }

            Block block = matchBlock();
            if(block != null) {
                blocks.add(block);
                continue;
            }

            if(T(0) == null)
                continue;
            
            if(T(0).type == Lexer.TOKEN_ID) {
                Rule rule = matchRule();
                if(rule != null)
                    rules.add(rule);
            } else if(T(0).type == Lexer.TOKEN_SINGLE_COMMENT) {
                Group group = matchRuleGroup(rules);
                if(group != null)
                    groups.add(group);
            }
        }
    }

    public List getLines() {
        if(lexer == null)
            return null;
        else
            return lexer.lines;
    }

    public int getMaxLines() {
        if(lexer == null)
            return 0;
        else
            return lexer.line;
    }

    public Name matchName() {
        Token start = T(0);

        if(start.type != Lexer.TOKEN_ID)
            return null;

        if(start.getAttribute().equals("grammar")) {
            while(nextToken()) {
                if(T(0).type == Lexer.TOKEN_SEMI)
                    return new Name(start.getAttribute(), start, T(0));
            }
            return null;
        } else
            return null;
    }

    public Block matchBlock() {
        Token start = T(0);
        if(start == null)
            return null;

        if(start.type != Lexer.TOKEN_ID)
            return null;

        if(blockIdentifiers.contains(start.getAttribute().toLowerCase())) {
            if(T(1) == null)
                return null;

            if(T(1).type != Lexer.TOKEN_BLOCK)
                return null;

            nextToken();
            return new Block(start.getAttribute(), start, T(0));
        } else
            return null;
    }

    public Rule matchRule() {
        Token start = T(0);
        if(start == null)
            return null;

        String name = start.getAttribute();

        if(start.getAttribute().equals(ATTRIBUTE_FRAGMENT)) {
            nextToken();
            name = T(0).getAttribute();
        }

        // Skip any comments
        while(T(1) != null && (T(1).type == Lexer.TOKEN_SINGLE_COMMENT || T(1).type == Lexer.TOKEN_COMPLEX_COMMENT)) {
            nextToken();
        }

        // Make sure it is a rule
        if(T(1) == null || T(1).type != Lexer.TOKEN_COLON) {
            // @todo check for that but a rule name should always be followed by a colon ?
            return null;
        }

        boolean colonFound = false;
        while(nextToken()) {
            if(T(0).type == Lexer.TOKEN_SEMI) {
                break;
            }
            if(T(0).type == Lexer.TOKEN_COLON) {
                colonFound = true;
                break;
            }
        }
        if(!colonFound)
            return null;

        while(nextToken()) {
            if(T(0).type == Lexer.TOKEN_SEMI)
                return new Rule(name, start, T(0));
        }
        return null;
    }

    public Group matchRuleGroup(List rules) {
        Token token = T(0);
        String comment = token.getAttribute();

        if(comment.startsWith(BEGIN_GROUP)) {
            return new Group(comment.substring(BEGIN_GROUP.length(), comment.length()-1), rules.size()-1, token);
        } else if(comment.startsWith(END_GROUP)) {
                return new Group(rules.size()-1, token);
        } else
            return null;
    }

    public boolean nextToken() {
        position++;
        return position<tokens.size();
    }

    public Token T(int index) {
        if(position+index<tokens.size())
            return (Token)tokens.get(position+index);
        else
            return null;
    }

    public class Rule implements Comparable {

        public String name;
        public Token start;
        public Token end;

        public boolean isAllUpperCase = false;

        public List errors;

        public Rule(String name, Token start, Token end) {
            this.name = name;
            this.start = start;
            this.end = end;
            this.isAllUpperCase =  name.equals(name.toUpperCase());
        }

        public int getStartIndex() {
            return start.getStartIndex();
        }

        public int getEndIndex() {
            return end.getEndIndex();
        }

        public int getLength() {
            return getEndIndex()-getStartIndex();
        }

        public int getInternalTokensStartIndex() {
            for(Iterator iter = getTokens().iterator(); iter.hasNext(); ) {
                Token token = (Token)iter.next();
                if(token.getAttribute().equals(":")) {
                    token = (Token)iter.next();
                    return token.getStartIndex();
                }
            }
            return -1;
        }

        public int getInternalTokensEndIndex() {
            Token token = (Token)tokens.get(tokens.indexOf(end)-1);
            return token.getEndIndex();
        }

        public List getBlocks() {
            List blocks = new ArrayList();
            Token lastToken = null;
            for(int index=tokens.indexOf(start); index<tokens.indexOf(end); index++) {
                Token token = (Token)tokens.get(index);
                if(token.type == Lexer.TOKEN_BLOCK) {
                    if(lastToken != null && lastToken.type == Lexer.TOKEN_ID && lastToken.getAttribute().equals("options"))
                        continue;

                    blocks.add(token);
                }
                lastToken = token;
            }
            return blocks;
        }

        public List getTokens() {
            List t = new ArrayList();
            for(int index=tokens.indexOf(start); index<tokens.indexOf(end); index++) {
                t.add(tokens.get(index));
            }
            return t;
        }

        public List getAlternatives() {
            List alts = new ArrayList();
            List alt = null;
            boolean findColon = true;
            int level = 0;
            for(Iterator iter = getTokens().iterator(); iter.hasNext(); ) {
                Token token = (Token)iter.next();
                if(findColon) {
                    if(token.getAttribute().equals(":")) {
                        findColon = false;
                        alt = new ArrayList();
                    }
                } else {
                    if(token.getAttribute().equals("("))
                        level++;
                    else if(token.getAttribute().equals(")"))
                        level--;
                    else if(token.type != Lexer.TOKEN_BLOCK && level == 0) {
                        if(token.getAttribute().equals("|")) {
                            alts.add(alt);
                            alt = new ArrayList();
                            continue;
                        }
                    }
                    alt.add(token);
                }
            }
            if(alt != null && !alt.isEmpty())
                alts.add(alt);
            return alts;
        }

        public void setErrors(List errors) {
            this.errors = errors;
        }

        public boolean isLexerRule() {
            return isAllUpperCase;
        }

        public boolean hasLeftRecursion() {
            for(Iterator iter = getAlternatives().iterator(); iter.hasNext(); ) {
                List alts = (List)iter.next();
                if(alts.isEmpty())
                    continue;
                
                Token firstTokenInAlt = (Token)alts.get(0);
                if(firstTokenInAlt.getAttribute().equals(name))
                    return true;
            }
            return false;
        }

        public String getTextRuleAfterRemovingLeftRecursion() {
            StringBuffer head = new StringBuffer();
            StringBuffer star = new StringBuffer();

            for(Iterator iter = getAlternatives().iterator(); iter.hasNext(); ) {
                List alts = (List)iter.next();
                Token firstTokenInAlt = (Token)alts.get(0);
                if(firstTokenInAlt.getAttribute().equals(name)) {
                    if(alts.size() > 1) {
                        if(star.length() > 0)
                            star.append(" | ");
                        int start = ((Token)alts.get(1)).getStartIndex();
                        int end = ((Token)alts.get(alts.size()-1)).getEndIndex();
                        star.append(firstTokenInAlt.text.substring(start, end));
                    }
                } else {
                    if(head.length() > 0)
                        head.append(" | ");
                    int start = firstTokenInAlt.getStartIndex();
                    int end = ((Token)alts.get(alts.size()-1)).getEndIndex();
                    head.append(firstTokenInAlt.text.substring(start, end));
                }
            }

            StringBuffer sb = new StringBuffer();
            sb.append("(");
            sb.append(head);
            sb.append(") ");
            sb.append("(");
            sb.append(star);
            sb.append(")*");

            return sb.toString();
        }

        public boolean hasErrors() {
            if(errors == null)
                return false;
            else
                return errors.size()>0;
        }

        public String getErrorMessageString(int index) {
            GrammarEngineError error = (GrammarEngineError) errors.get(index);
            return error.message;
        }

        public String getErrorMessageHTML() {
            StringBuffer message = new StringBuffer();
            message.append("<html>");
            for (Iterator iterator = errors.iterator(); iterator.hasNext();) {
                GrammarEngineError error = (GrammarEngineError) iterator.next();
                message.append(error.message);
                if(iterator.hasNext())
                    message.append("<br>");
            }
            message.append("</html>");
            return message.toString();
        }

        public String toString() {
            return name;
        }

        public int compareTo(Object o) {
            Rule otherRule = (Rule) o;
            return this.name.compareTo(otherRule.name);
        }
    }

    public class Block {

        public String name;
        public Token start;
        public Token end;

        public Block(String name, Token start, Token end) {
            this.name = name;
            this.start = start;
            this.end = end;
        }
    }

    public class Name {

        public String name;
        public Token start;
        public Token end;

        public Name(String name, Token start, Token end) {
            this.name = name;
            this.start = start;
            this.end = end;
        }
    }

    public class Group {

        public String name = null;
        public int ruleIndex = -1;
        public boolean openGroup = false;
        public Token token = null;

        public Group(String name, int ruleIndex, Token token) {
            this.name = name;
            this.ruleIndex = ruleIndex;
            this.token = token;
            this.openGroup = true;
        }

        public Group(int ruleIndex, Token token) {
            this.ruleIndex = ruleIndex;
            this.token = token;
            this.openGroup = false;
        }

        public String toString() {
            return "Group "+name+", open ="+openGroup+", ruleIndex = "+ruleIndex;
        }

    }
}