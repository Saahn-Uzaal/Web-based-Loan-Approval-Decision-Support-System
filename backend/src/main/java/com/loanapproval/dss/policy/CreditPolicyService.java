package com.loanapproval.dss.policy;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

@Service
public class CreditPolicyService {

    private final CreditPolicyRepository creditPolicyRepository;

    @Autowired
    public CreditPolicyService(@Nullable CreditPolicyRepository creditPolicyRepository) {
        this.creditPolicyRepository = creditPolicyRepository;
    }

    public CreditPolicyDefinition currentPolicy() {
        if (creditPolicyRepository == null) {
            return CreditPolicyDefinition.defaultPolicy();
        }
        return creditPolicyRepository.findActivePolicy().orElseGet(CreditPolicyDefinition::defaultPolicy);
    }

    public String currentVersion() {
        return currentPolicy().version();
    }
}
