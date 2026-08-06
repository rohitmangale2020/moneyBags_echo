package com.training.platform.customers.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name ="customerdocument")

public class CustomerDocumentEntity {
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        @Column(name ="document_id")
        private Long documentId;

}
