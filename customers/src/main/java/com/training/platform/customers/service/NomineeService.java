package com.training.platform.customers.service;

import com.training.platform.customers.dto.NomineeRequestDto;
import com.training.platform.customers.dto.NomineeResponseDto;

import java.util.List;

public interface NomineeService {

    NomineeResponseDto createNominee(Long customerId, NomineeRequestDto requestDto);

    List<NomineeResponseDto> getAllNomineesByCustomerId(Long customerId);

    NomineeResponseDto getNomineeById(Long customerId, Long nomineeId);

    NomineeResponseDto updateNominee(Long customerId, Long nomineeId, NomineeRequestDto requestDto);

    NomineeResponseDto closeNominee(Long customerId, Long nomineeId);

    void deleteNominee(Long customerId, Long nomineeId);
}
