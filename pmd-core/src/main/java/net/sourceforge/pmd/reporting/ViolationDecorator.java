/*
 * BSD-style license; for more info see http://pmd.sourceforge.net/license.html
 */

package net.sourceforge.pmd.reporting;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.sourceforge.pmd.RuleViolation;
import net.sourceforge.pmd.lang.ast.Node;

/**
 * Adds extra key/value pairs to a violation in a language-specific manner.
 * The keys are completely free. {@link RuleViolation} defines some of these keys.
 */
@FunctionalInterface
public interface ViolationDecorator {

    /**
     * Compute extra key/value pairs about the violation that should be
     * reflected in {@link RuleViolation#getAdditionalInfo()}. This extra
     * info should be accumulated into the {@code extraData} parameter.
     *
     * @param violation     Violation
     * @param violationNode The node on which the violation was reported
     * @param extraData     Accumulator
     */
    void decorate(RuleViolation violation, Node violationNode, Map<String, String> extraData);

    static RuleViolation apply(ViolationDecorator decorator, RuleViolation violation, Node violationNode) {
        Map<String, String> extraData = new HashMap<>();
        decorator.decorate(violation, violationNode, extraData);
        if (!extraData.isEmpty()) {
            return new DecoratedRuleViolation(violation, extraData);
        } else {
            return violation;
        }
    }

    /**
     * Apply several decorators in a chain.
     */
    static ViolationDecorator chain(List<? extends ViolationDecorator> list) {
        return (rv, node, map) -> {
            for (ViolationDecorator decorator : list) {
                decorator.decorate(rv, node, map);
            }
        };
    }

    static ViolationDecorator noop() {
        return (rv, node, map) -> { };
    }
}
