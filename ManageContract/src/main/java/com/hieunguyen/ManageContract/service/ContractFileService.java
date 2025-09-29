package com.hieunguyen.ManageContract.service;

import com.hieunguyen.ManageContract.entity.Contract;
import com.hieunguyen.ManageContract.entity.ContractSignature;

public interface ContractFileService {

    String generateContractFile(Contract contract);

    /**
     * Chèn ảnh chữ ký (URL) vào file hợp đồng.
     * Nếu có placeholderKey, sẽ thay thế {{SIGN:<key>}} trong HTML.
     * Nếu không có, sẽ append đoạn <img> vào cuối file (HTML).
     */
    String embedSignatureFromUrl(
            String filePath,
            String imageUrl,
            Integer page,      // để dành cho PDF/DOCX, HTML sẽ bỏ qua
            Float x,
            Float y,
            Float width,
            Float height,
            String placeholderKey
    );

    /**
     * Chèn chữ (tên người ký) thay ảnh, font-size & bold theo yêu cầu.
     * Nếu có placeholderKey sẽ replace {{SIGN:<key>}}.
     */
    String embedSignatureText(
            String filePath,
            String signerName,
            Integer page,
            Float x,
            Float y,
            int fontSize,
            boolean bold,
            String placeholderKey
    );

    /**
     * (tuỳ chọn) nếu bạn muốn chèn theo thực thể ContractSignature đã lưu DB.
     */
    default String embedSignature(String filePath, ContractSignature signature) {
        if (signature.getSignatureImage() != null) {
            return embedSignatureFromUrl(
                    filePath,
                    signature.getSignatureImage(),
                    signature.getPage(),
                    signature.getX(),
                    signature.getY(),
                    signature.getWidth(),
                    signature.getHeight(),
                    signature.getPlaceholderKey()
            );
        }
        return filePath;
    }
}
