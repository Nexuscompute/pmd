/*
 * BSD-style license; for more info see http://pmd.sourceforge.net/license.html
 */

package net.sourceforge.pmd.lang.apex.ast;

import com.google.summit.ast.initializer.ConstructorInitializer;

public class ASTNewSetInitExpression extends AbstractApexNode.Single<ConstructorInitializer> {

    ASTNewSetInitExpression(ConstructorInitializer newSetInitExpression) {
        super(newSetInitExpression);
    }

    @Override
    public Object jjtAccept(ApexParserVisitor visitor, Object data) {
        return visitor.visit(this, data);
    }
}
