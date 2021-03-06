//$Header: /Development/AEDevelopment/projects/org.activebpel.rt.bpel/src/org/activebpel/rt/bpel/def/validation/activity/AeWSBPELActivityScopeValidator.java,v 1.8.2.1 2008/04/21 16:09:42 ppatruni Exp $
/////////////////////////////////////////////////////////////////////////////
//PROPRIETARY RIGHTS STATEMENT
//The contents of this file represent confidential information that is the 
//proprietary property of Active Endpoints, Inc.  Viewing or use of 
//this information is prohibited without the express written consent of 
//Active Endpoints, Inc. Removal of this PROPRIETARY RIGHTS STATEMENT 
//is strictly forbidden. Copyright (c) 2002-2006 All rights reserved. 
/////////////////////////////////////////////////////////////////////////////
package org.activebpel.rt.bpel.def.validation.activity;

import org.activebpel.rt.bpel.def.AeCompensationHandlerDef;
import org.activebpel.rt.bpel.def.AeFaultHandlersDef;
import org.activebpel.rt.bpel.def.AeTerminationHandlerDef;
import org.activebpel.rt.bpel.def.IAeFCTHandlerDef;
import org.activebpel.rt.bpel.def.activity.AeActivityCompensateDef;
import org.activebpel.rt.bpel.def.activity.AeActivityCompensateScopeDef;
import org.activebpel.rt.bpel.def.activity.AeActivityScopeDef;
import org.activebpel.rt.bpel.def.validation.IAeValidationDefs;
import org.activebpel.rt.bpel.def.visitors.AeAbstractDefVisitor;
import org.activebpel.rt.bpel.def.visitors.AeAbstractSearchVisitor;
import org.activebpel.rt.bpel.def.visitors.AeDefTraverser;
import org.activebpel.rt.bpel.def.visitors.AeTraversalVisitor;

import java.util.HashSet;
import java.util.Set;

/**
 * WS-BPEL scope validator
 */
public class AeWSBPELActivityScopeValidator extends AeActivityScopeValidator {

    /**
     * Ctor
     *
     * @param aDef
     */
    public AeWSBPELActivityScopeValidator(AeActivityScopeDef aDef) {
        super(aDef);
    }

    /**
     * @see org.activebpel.rt.bpel.def.validation.activity.AeActivityScopeValidator#validateIsolatedScope()
     */
    protected void validateIsolatedScope() {
        // must not be nested within an isolated scope
        for (AeActivityScopeValidator parent = getAnscestor(AeActivityScopeValidator.class); parent != null; parent = parent.getAnscestor(AeActivityScopeValidator.class)) {
            if (parent.getDef().isIsolated()) {
                getReporter().reportProblem(WSBPEL_NESTED_ISOLATED_SCOPE_CODE,
                        IAeValidationDefs.ERROR_NESTED_ISOLATED_SCOPE, null,
                        getDefinition());
                break;
            }
        }

        // if we're within an FCT handler...
        if (isWithinFCTHandler()) {
            // get list of scope names referenced by compensateScope or compensate
            // activities
            AeTargetedScopesVisitor targetedScopesVisitor = new AeTargetedScopesVisitor();
            getDef().accept(targetedScopesVisitor);

            if (targetedScopesVisitor.isCompensateFound() || !targetedScopesVisitor.getScopeNames().isEmpty()) {
                // if set is not empty, then visit our enclosing scope's main branch
                AeActivityScopeDef enclosingScopeDef = getEnclosingScopeDef();

                // traverse each of the referenced scopes by name (or all scopes if
                // <compensate> found). if an isolated scope is encountered, report
                // error with this scope's path and the path of the isolated scope
                AeIsolatedScopeSearchVisitor isolatedSearchVisitor = new AeIsolatedScopeSearchVisitor();
                isolatedSearchVisitor.setWildcard(targetedScopesVisitor.isCompensateFound());
                isolatedSearchVisitor.setNames(targetedScopesVisitor.getScopeNames());

                enclosingScopeDef.getActivityDef().accept(isolatedSearchVisitor);

                if (!isolatedSearchVisitor.getMatchedScopes().isEmpty()) {
                    // one error for the src of the problem
                    getReporter().reportProblem(WSBPEL_NESTED_ISOLATED_SCOPE_FCT_SOURCE_CODE,
                            IAeValidationDefs.ERROR_NESTED_ISOLATED_SCOPE_FCT_SOURCE, null,
                            getDefinition());

                    for (AeActivityScopeDef def : isolatedSearchVisitor.getMatchedScopes()) {
                        // and one error for each one of the targets
                        getReporter().reportProblem(WSBPEL_NESTED_ISOLATED_SCOPE_FCT_TARGET_CODE,
                                IAeValidationDefs.ERROR_NESTED_ISOLATED_SCOPE_FCT_TARGET, new String[]{getDef().getLocationPath()},
                                def);

                    }
                }
            }
        }
    }

    /**
     * Gets the def for the enclosing scope.
     */
    private AeActivityScopeDef getEnclosingScopeDef() {
        AeActivityScopeValidator enclosingScopeValidator = getAnscestor(AeActivityScopeValidator.class);
        AeActivityScopeDef enclosingScopeDef = enclosingScopeValidator.getDef();
        return enclosingScopeDef;
    }

    /**
     * Returns true if the scope is nested within an FCT handler.
     */
    protected boolean isWithinFCTHandler() {
        return enclosedWithinDef(IAeFCTHandlerDef.class);
    }

    /**
     * Produces a list of the scopes that can be compensated from this isolated
     * scope. The visitor will stop traversing if it encounters a <compensate/>
     * since that will compensate all scopes.
     */
    protected static class AeTargetedScopesVisitor extends AeAbstractSearchVisitor {
        /**
         * set of scope names that are being targeted by <compensateScope/>
         * activities
         */
        private Set<String> mScopeNames = new HashSet<>();

        /**
         * set to true if we encounter a <compensate/>
         */
        private boolean mCompensateFound = false;

        /**
         * @see org.activebpel.rt.bpel.def.visitors.AeAbstractDefVisitor#visit(org.activebpel.rt.bpel.def.activity.AeActivityCompensateDef)
         */
        public void visit(AeActivityCompensateDef def) {
            setCompensateFound(true);
        }

        /**
         * @see org.activebpel.rt.bpel.def.visitors.AeAbstractDefVisitor#visit(org.activebpel.rt.bpel.def.activity.AeActivityCompensateScopeDef)
         */
        public void visit(AeActivityCompensateScopeDef def) {
            getScopeNames().add(def.getTarget());
            super.visit(def);
        }

        /**
         * @see org.activebpel.rt.bpel.def.visitors.AeAbstractDefVisitor#visit(org.activebpel.rt.bpel.def.AeCompensationHandlerDef)
         */
        public void visit(AeCompensationHandlerDef def) {
            // do not traverse into an FCT handler. We're only interested in
            // finding <compensate/> or <compensateScope/> activities that can target
            // our enclosing scope for compensation.
        }

        /**
         * @see org.activebpel.rt.bpel.def.visitors.AeAbstractDefVisitor#visit(org.activebpel.rt.bpel.def.AeFaultHandlersDef)
         */
        public void visit(AeFaultHandlersDef def) {
            // do not traverse into an FCT handler. We're only interested in
            // finding <compensate/> or <compensateScope/> activities that can target
            // our enclosing scope for compensation.
        }

        /**
         * @see org.activebpel.rt.bpel.def.visitors.AeAbstractDefVisitor#visit(org.activebpel.rt.bpel.def.AeTerminationHandlerDef)
         */
        public void visit(AeTerminationHandlerDef def) {
            // do not traverse into an FCT handler. We're only interested in
            // finding <compensate/> or <compensateScope/> activities that can target
            // our enclosing scope for compensation.
        }

        /**
         * @see org.activebpel.rt.bpel.def.visitors.AeAbstractSearchVisitor#isFound()
         */
        public boolean isFound() {
            return isCompensateFound();
        }

        /**
         * @return the compensateFound
         */
        protected boolean isCompensateFound() {
            return mCompensateFound;
        }

        /**
         * @param aCompensateFound the compensateFound to set
         */
        protected void setCompensateFound(boolean aCompensateFound) {
            mCompensateFound = aCompensateFound;
        }

        /**
         * @return the scopeNames
         */
        protected Set<String> getScopeNames() {
            return mScopeNames;
        }

        /**
         * @param aScopeNames the scopeNames to set
         */
        protected void setScopeNames(Set<String> aScopeNames) {
            mScopeNames = aScopeNames;
        }
    }

    /**
     * Searches the enclosed scope's main branch to locate any isolated scopes
     * that are targeted by a &lt;compensateScope&gt; activity OR any isolated scopes
     * if there was a &lt;compensate&gt; found in a FCT handler.
     */
    protected static class AeIsolatedScopeSearchVisitor extends AeAbstractDefVisitor {
        /**
         * set of scope names we're matching on
         */
        private Set mNames;
        /**
         * wildcard to indicate we're matching on all scopes
         */
        private boolean mWildcard;
        /**
         * tracks the depth to avoid matching nested scopes by name
         */
        private int mLevel = 1;
        /**
         * Set of matched isolated scopes
         */
        private final Set<AeActivityScopeDef> mMatchedScopes = new HashSet<>();

        /**
         * Visitor will match on any isolated scopes
         */
        public AeIsolatedScopeSearchVisitor() {
            setTraversalVisitor(new AeTraversalVisitor(new AeDefTraverser(), this));
        }

        /**
         * @see org.activebpel.rt.bpel.def.visitors.AeAbstractDefVisitor#visit(org.activebpel.rt.bpel.def.activity.AeActivityScopeDef)
         */
        public void visit(AeActivityScopeDef def) {
            // we've encountered a scope
            // if it's isolated, then check to see if it's one we're looking for
            if (def.isIsolated()) {
                if (isMatch(def)) {
                    getMatchedScopes().add(def);
                }
            }

            // whether the scope is isolated or not, we need to decide if we
            // should traverse it.
            // If the wildcard is enabled, then we should traverse all scopes.
            // If no wildcard, we should only traverse scopes that are at level 1
            // and within our collection of names.
            // If our current level > 1 then we must have already traversed into a
            // matched scope so keep going.
            if (isMatch(def)) {
                int level = getLevel();
                setLevel(level + 1);
                super.visit(def);
                setLevel(level);
            }
        }

        /**
         * Returns true if this scope is one that we're looking for, either
         * because we're matching with a wildcard or it's targeted by a
         * &lt;compensateScope&gt;.
         * <p/>
         * If the wildcard is true, then we match all scopes.
         * <p/>
         * If the current level > 1, then we match all scopes since it means we
         * must have traversed into a scope that we matched on.
         * <p/>
         * If the current level = 1, then we match on the scope if it's name is
         * one of the ones we're looking for.
         *
         * @param aActivityScopeDef
         */
        protected boolean isMatch(AeActivityScopeDef aActivityScopeDef) {
            return isWildcard() || (getLevel() > 1) || (getLevel() == 1 && getNames().contains(aActivityScopeDef.getName()));
        }

        /**
         * @return the level
         */
        protected int getLevel() {
            return mLevel;
        }

        /**
         * @param aLevel the level to set
         */
        protected void setLevel(int aLevel) {
            mLevel = aLevel;
        }

        /**
         * @return the names
         */
        protected Set getNames() {
            return mNames;
        }

        /**
         * @param aNames the names to set
         */
        public void setNames(Set aNames) {
            mNames = aNames;
        }

        /**
         * @return the wildcard
         */
        public boolean isWildcard() {
            return mWildcard;
        }

        /**
         * @param aWildcard the wildcard to set
         */
        protected void setWildcard(boolean aWildcard) {
            mWildcard = aWildcard;
        }

        /**
         * @return the scopeLocationPaths
         */
        public Set<AeActivityScopeDef> getMatchedScopes() {
            return mMatchedScopes;
        }
    }
}
 