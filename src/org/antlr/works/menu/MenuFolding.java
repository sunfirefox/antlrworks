package org.antlr.works.menu;

import org.antlr.works.components.grammar.CEditorGrammar;
import org.antlr.works.syntax.GrammarSyntaxAction;
import org.antlr.works.syntax.GrammarSyntaxRule;

import java.util.List;
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

public class MenuFolding extends MenuAbstract {

    public MenuFolding(CEditorGrammar editor) {
        super(editor);
    }

    public void expandCollapseAction() {
        editor.foldingManager.toggleFolding(editor.getCurrentAction());
    }

    public void expandCollapseRule() {
        editor.foldingManager.toggleFolding(editor.getCurrentRule());
    }

    public void expandAllRules() {
        expandCollapseAllRules(true);
    }

    public void collapseAllRules() {
        expandCollapseAllRules(false);
    }

    public void expandCollapseAllRules(boolean expand) {
        List rules = editor.rules.getRules();
        if(rules == null)
            return;

        for(int i = 0; i<rules.size(); i++) {
            GrammarSyntaxRule rule = (GrammarSyntaxRule)rules.get(i);
            if(rule.foldingEntityIsExpanded() != expand) {
                editor.foldingManager.toggleFolding(rule);
            }
        }
    }

    public void expandAllActions() {
        expandCollapseAllActions(true);
    }

    public void collapseAllActions() {
        expandCollapseAllActions(false);
    }

    public void expandCollapseAllActions(boolean expand) {
        List actions = editor.getActions();
        if(actions == null)
            return;

        for(int i = 0; i<actions.size(); i++) {
            GrammarSyntaxAction action = (GrammarSyntaxAction)actions.get(i);
            if(action.foldingEntityIsExpanded() != expand) {
                editor.foldingManager.toggleFolding(action);
            }
        }
    }

}
