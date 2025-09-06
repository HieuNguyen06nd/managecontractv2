package com.hieunguyen.ManageContract.service;

import com.hieunguyen.ManageContract.entity.AuthAccount;
import com.hieunguyen.ManageContract.entity.Contract;

import java.util.Map;

public interface ContractService {
    Contract createContract(Long templateId, String title, Map<String, String> variableValues, AuthAccount createdBy);
}
