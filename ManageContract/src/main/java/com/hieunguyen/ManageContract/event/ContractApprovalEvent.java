package com.hieunguyen.ManageContract.event;
import com.hieunguyen.ManageContract.common.constants.ApprovalStatus;

public record ContractApprovalEvent(Long approvalId, Long contractId, ApprovalStatus status) {}
