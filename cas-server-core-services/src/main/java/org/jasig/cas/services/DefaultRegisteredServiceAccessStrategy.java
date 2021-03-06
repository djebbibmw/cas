package org.jasig.cas.services;

import org.jasig.cas.util.RegexUtils;

import com.google.common.base.Predicates;
import com.google.common.collect.Sets;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * This is {@link DefaultRegisteredServiceAccessStrategy}
 * that allows the following rules:
 *
 * <ul>
 *     <li>A service may be disallowed to use CAS for authentication</li>
 *     <li>A service may be disallowed to take part in CAS single sign-on such that
 *     presentation of credentials would always be required.</li>
 *     <li>A service may be prohibited from receiving a service ticket
 *     if the existing principal attributes don't contain the required attributes
 *     that otherwise grant access to the service.</li>
 * </ul>
 *
 * @author Misagh Moayyed mmoayyed@unicon.net
 * @since 4.1
 */
public class DefaultRegisteredServiceAccessStrategy implements RegisteredServiceAccessStrategy {

    private static final long serialVersionUID = 1245279151345635245L;

    protected final Logger logger = LoggerFactory.getLogger(this.getClass());

    /** Is the service allowed at all? **/
    private boolean enabled = true;

    /** Is the service allowed to use SSO? **/
    private boolean ssoEnabled = true;

    private URI unauthorizedRedirectUrl;

    /**
     * Defines the attribute aggregation behavior when checking for required attributes.
     * Default requires that all attributes be present and match the principal's.
     */
    private boolean requireAllAttributes = true;

    /**
     * Collection of required attributes
     * for this service to proceed.
     */
    private Map<String, Set<String>> requiredAttributes = new HashMap<>();

    /**
     * Collection of attributes
     * that will be rejected which will cause this
     * policy to refuse access.
     */
    private Map<String, Set<String>> rejectedAttributes = new HashMap<>();
    
    /**
     * Indicates whether matching on required attribute values
     * should be done in a case-insensitive manner.
     */
    private boolean caseInsensitive;

    /**
     * Instantiates a new Default registered service authorization strategy.
     * By default, rules indicate that services are both enabled
     * and can participate in SSO.
     */
    public DefaultRegisteredServiceAccessStrategy() {
        this(true, true);
    }

    /**
     * Instantiates a new Default registered service authorization strategy.
     *
     * @param enabled the enabled
     * @param ssoEnabled the sso enabled
     */
    public DefaultRegisteredServiceAccessStrategy(final boolean enabled, final boolean ssoEnabled) {
        this.enabled = enabled;
        this.ssoEnabled = ssoEnabled;
    }

    public final void setEnabled(final boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * Set to enable/authorize this service.
     * @param ssoEnabled true to enable service
     */
    public final void setSsoEnabled(final boolean ssoEnabled) {
        this.ssoEnabled = ssoEnabled;
    }

    public boolean isEnabled() {
        return this.enabled;
    }

    public boolean isSsoEnabled() {
        return this.ssoEnabled;
    }

    /**
     * Defines the attribute aggregation when checking for required attributes.
     * Default requires that all attributes be present and match the principal's.
     * @param requireAllAttributes the require all attributes
     */
    public final void setRequireAllAttributes(final boolean requireAllAttributes) {
        this.requireAllAttributes = requireAllAttributes;
    }

    public final boolean isRequireAllAttributes() {
        return this.requireAllAttributes;
    }

    public Map<String, Set<String>> getRequiredAttributes() {
        return new HashMap<>(this.requiredAttributes);
    }

    public void setUnauthorizedRedirectUrl(final URI unauthorizedRedirectUrl) {
        this.unauthorizedRedirectUrl = unauthorizedRedirectUrl;
    }

    @Override
    public URI getUnauthorizedRedirectUrl() {
        return this.unauthorizedRedirectUrl;
    }

    /**
     * Is attribute value matching case insensitive?
     *
     * @return true/false
     */
    public boolean isCaseInsensitive() {
        return caseInsensitive;
    }

    /**
     * Sets case insensitive.
     *
     * @param caseInsensitive the case insensitive
     * @since 4.3
     */
    public void setCaseInsensitive(final boolean caseInsensitive) {
        this.caseInsensitive = caseInsensitive;
    }

    /**
     * Defines the required attribute names and values that
     * must be available to the principal before the flow
     * can proceed to the next step. Every attribute in
     * the map can be linked to multiple values.
     *
     * @param requiredAttributes the required attributes
     */
    public final void setRequiredAttributes(final Map<String, Set<String>> requiredAttributes) {
        this.requiredAttributes = requiredAttributes;
    }

    /**
     * Sets rejected attributes. If the policy finds any of the attributes defined
     * here, it will simply reject and refuse access. 
     *
     * @param rejectedAttributes the rejected attributes
     */
    public void setRejectedAttributes(final Map<String, Set<String>> rejectedAttributes) {
        this.rejectedAttributes = rejectedAttributes;
    }

    /**
     * {@inheritDoc}
     *
     * Verify presence of service required attributes.
     * <ul>
     *     <li>If no rejected attributes are specified, authz is granted.</li>
     *     <li>If no required attributes are specified, authz is granted.</li>
     *     <li>If ALL attributes must be present, and the principal contains all and there is
     *     at least one attribute value that matches the rejected, authz is denied.</li>
     *     <li>If ALL attributes must be present, and the principal contains all and there is
     *     at least one attribute value that matches the required, authz is granted.</li>
     *     <li>If ALL attributes don't have to be present, and there is at least
     *     one principal attribute present whose value matches the rejected, authz is denied.</li>
     *     <li>If ALL attributes don't have to be present, and there is at least
     *     one principal attribute present whose value matches the required, authz is granted.</li>
     *     <li>Otherwise, access is denied</li>
     * </ul>
     */
    @Override
    public boolean doPrincipalAttributesAllowServiceAccess(final String principal, final Map<String, Object> principalAttributes) {
        if (this.rejectedAttributes.isEmpty() && this.requiredAttributes.isEmpty()) {
            logger.debug("Skipping access strategy policy, since no attributes rules are defined");
            return true;
        }
        
        if (!enoughAttributesAvailableToProcess(principal, principalAttributes)) {
            logger.debug("Access is denied. There are not enough attributes available to satisfy requirements");
            return false;
        }

        if (doRejectedAttributesRefusePrincipalAccess(principalAttributes)) {
            logger.debug("Access is denied. The principal carries attributes that would reject service access");
            return false;
        }
                
        if (!doRequiredAttributesAllowPrincipalAccess(principalAttributes)) {
            logger.debug("Access is denied. The principal does not have the required attributes specified by this strategy");
            return false;
        }


        return true;
    }

    private boolean doRequiredAttributesAllowPrincipalAccess(final Map<String, Object> principalAttributes) {
        logger.debug("These required attributes [{}] are examined against [{}] before service can proceed.",
                this.requiredAttributes, principalAttributes);
        final Sets.SetView<String> difference = Sets.intersection(this.requiredAttributes.keySet(), principalAttributes.keySet());
        final Set<String> copy = difference.immutableCopy();

        if (this.requiredAttributes.isEmpty()) {
            logger.debug("No required attributes are defined");
            return true;
        }
        
        if (this.requireAllAttributes && copy.size() < this.requiredAttributes.size()) {
            logger.debug("Not all required attributes are available to the principal");
            return false;
        }
        
        return copy.stream().filter(key -> {
            final Set<String> requiredValues = this.requiredAttributes.get(key);
            final Set<String> availableValues;

            final Object objVal = principalAttributes.get(key);
            if (objVal instanceof Collection) {
                availableValues = Sets.newHashSet(((Collection) objVal).iterator());
            } else {
                availableValues = Sets.newHashSet(objVal.toString());
            }

            final Set<?> differenceInValues;
            final Pattern pattern = RegexUtils.concatenate(requiredValues, this.caseInsensitive);
            if (pattern != null) {
                differenceInValues = Sets.filter(availableValues, Predicates.contains(pattern));
            } else {
                differenceInValues = Sets.intersection(availableValues, requiredValues);
            }

            if (!differenceInValues.isEmpty()) {
                logger.info("Principal is authorized to access the service");
                return true;
            }
            return false;
        }).findFirst().isPresent();
    }
    
    private boolean doRejectedAttributesRefusePrincipalAccess(final Map<String, Object> principalAttributes) {
        logger.debug("These rejected attributes [{}] are examined against [{}] before service can proceed.",
                this.rejectedAttributes, principalAttributes);
        final Sets.SetView<String> rejectedDifference = Sets.intersection(this.rejectedAttributes.keySet(), principalAttributes.keySet());
        final Set<String> rejectedCopy = rejectedDifference.immutableCopy();

        if (this.rejectedAttributes.isEmpty()) {
            logger.debug("No rejected attributes are defined");
            return false;
        }
        
        if (this.requireAllAttributes && rejectedCopy.size() < rejectedAttributes.size()) {
            logger.debug("Not all rejected attributes are available to the process");
            return false;
        }
        
        return rejectedCopy.stream().filter(key -> {
            final Set<String> rejectedValues = this.rejectedAttributes.get(key);
            final Set<String> availableValues;

            final Object objVal = principalAttributes.get(key);
            if (objVal instanceof Collection) {
                availableValues = Sets.newHashSet(((Collection) objVal).iterator());
            } else {
                availableValues = Sets.newHashSet(objVal.toString());
            }

            final Set<?> differenceInValues;
            final Pattern pattern = RegexUtils.concatenate(rejectedValues, this.caseInsensitive);
            if (pattern != null) {
                differenceInValues = Sets.filter(availableValues, Predicates.contains(pattern));
            } else {
                differenceInValues = Sets.intersection(availableValues, rejectedValues);
            }

            if (!differenceInValues.isEmpty()) {
                logger.info("Principal is denied access since there are rejected attributes [{}] defined as [{}}",
                        key, differenceInValues);
                return true;
            }
            return false;
        }).findFirst().isPresent();
    }
    /**
     * Enough attributes available to process? Check collection sizes and determine
     * if we have enough data to move on. 
     *
     * @param principal           the principal
     * @param principalAttributes the principal attributes
     * @return true/false
     */
    protected boolean enoughAttributesAvailableToProcess(final String principal, final Map<String, Object> principalAttributes) {
        if (principalAttributes.isEmpty() && !this.requiredAttributes.isEmpty()) {
            logger.debug("No principal attributes are found to satisfy defined attribute requirements");
            return false;
        }

        if (principalAttributes.size() < this.rejectedAttributes.size()) {
            logger.debug("The size of the principal attributes that are [{}] does not match defined rejected attributes, "
                            + "which means the principal is not carrying enough data to grant authorization",
                    principalAttributes);
            return false;
        }

        if (principalAttributes.size() < this.requiredAttributes.size()) {
            logger.debug("The size of the principal attributes that are [{}] does not match defined required attributes, "
                        + "which means the principal is not carrying enough data to grant authorization",
                    principalAttributes);
            return false;
        }
        return true;
    }
    
    @Override
    public boolean isServiceAccessAllowedForSso() {
        if (!this.ssoEnabled) {
            logger.trace("Service is not authorized to participate in SSO.");
        }
        return this.ssoEnabled;
    }

    @Override
    public boolean isServiceAccessAllowed() {
        if (!this.enabled) {
            logger.trace("Service is not enabled in service registry.");
        }

        return this.enabled;
    }


    @Override
    public boolean equals(final Object obj) {
        if (obj == null) {
            return false;
        }
        if (obj == this) {
            return true;
        }
        if (obj.getClass() != getClass()) {
            return false;
        }
        final DefaultRegisteredServiceAccessStrategy rhs = (DefaultRegisteredServiceAccessStrategy) obj;
        return new EqualsBuilder()
                .append(this.enabled, rhs.enabled)
                .append(this.ssoEnabled, rhs.ssoEnabled)
                .append(this.requireAllAttributes, rhs.requireAllAttributes)
                .append(this.requiredAttributes, rhs.requiredAttributes)
                .append(this.unauthorizedRedirectUrl, rhs.unauthorizedRedirectUrl)
                .append(this.caseInsensitive, rhs.caseInsensitive)
                .append(this.rejectedAttributes, rhs.rejectedAttributes)
                .isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder()
                .append(this.enabled)
                .append(this.ssoEnabled)
                .append(this.requireAllAttributes)
                .append(this.requiredAttributes)
                .append(this.unauthorizedRedirectUrl)
                .append(this.caseInsensitive)
                .append(this.rejectedAttributes)
                .toHashCode();
    }


    @Override
    public String toString() {
        return new ToStringBuilder(this)
                .append("enabled", enabled)
                .append("ssoEnabled", ssoEnabled)
                .append("requireAllAttributes", requireAllAttributes)
                .append("requiredAttributes", requiredAttributes)
                .append("unauthorizedRedirectUrl", unauthorizedRedirectUrl)
                .append("caseInsensitive", caseInsensitive)
                .append("rejectedAttributes", rejectedAttributes)
                .toString();
    }


}
