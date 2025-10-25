package com.example.final_projects.service;

import com.example.final_projects.entity.UserTemplateRequest;
import com.example.final_projects.entity.UserTemplateRequestStatus;
import com.example.final_projects.repository.UserTemplateRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserTemplateRequestService {

    private final UserTemplateRequestRepository userTemplateRequestRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public UserTemplateRequest createInitialRequest(Long userId, String requestContent) {
        UserTemplateRequest userRequest = UserTemplateRequest.builder()
                .userId(userId)
                .requestContent(requestContent)
                .status(UserTemplateRequestStatus.PENDING)
                .build();
        return userTemplateRequestRepository.save(userRequest);
    }

    @Transactional
    public void markAsCompleted(Long userRequestId) {
        UserTemplateRequest userRequest = userTemplateRequestRepository.findById(userRequestId)
                .orElseThrow(() -> new IllegalArgumentException("UserTemplateRequest not found with id: " + userRequestId));

        userRequest.setStatus(UserTemplateRequestStatus.COMPLETED);
        userTemplateRequestRepository.save(userRequest);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markAsFailed(Long userRequestId) {
        UserTemplateRequest userRequest = userTemplateRequestRepository.findById(userRequestId)
                .orElseThrow(() -> new IllegalArgumentException("UserTemplateRequest not found with id: " + userRequestId));

        userRequest.setStatus(UserTemplateRequestStatus.FAILED);
        userTemplateRequestRepository.save(userRequest);
    }

    @Transactional(readOnly = true)
    public UserTemplateRequest findLatestPendingRequestByUserId(Long userId) {
        return userTemplateRequestRepository.findFirstByUserIdAndStatusOrderByIdDesc(userId, UserTemplateRequestStatus.PENDING)
                .orElse(null);
    }
}
