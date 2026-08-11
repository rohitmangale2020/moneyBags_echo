package com.training.platform.customers.service;

import com.training.platform.customers.dto.KycRequest;
import com.training.platform.customers.dto.KycResponse;

public interface KycService {

    KycResponse createKyc(Long customerId, KycRequest requestDto);

    KycResponse getKycByCustomerId(Long customerId);

    KycResponse updateKyc(Long customerId, KycRequest requestDto);
}