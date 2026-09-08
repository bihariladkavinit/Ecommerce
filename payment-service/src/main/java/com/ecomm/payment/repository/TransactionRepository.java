package com.ecomm.payment.repository;

import com.ecomm.payment.entity.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, UUID> {
    // Transactions are accessed via Payment.transactions (cascade).
    // This repository exists for direct queries if needed.
}
