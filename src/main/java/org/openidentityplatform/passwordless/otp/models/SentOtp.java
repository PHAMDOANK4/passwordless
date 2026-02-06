/*
 * Copyright 2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.openidentityplatform.passwordless.otp.models;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.openidentityplatform.passwordless.iam.models.User;

import java.util.UUID;

/**
 * Sent OTP Entity
 * Tracks OTP codes sent for authentication
 * Linked to User for centralized authentication tracking
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@ToString
@Entity
@Table(name = "sent_otp", indexes = {
    @Index(name = "idx_otp_destination", columnList = "destination"),
    @Index(name = "idx_otp_user", columnList = "user_id"),
    @Index(name = "idx_otp_expire", columnList = "expireTime")
})
public class SentOtp {

    @Id
    @Column(name = "session_id")
    private UUID sessionId;

    private String otp;

    private long expireTime;

    private String destination;

    private long lastSentAt;

    private Integer attempts;
    
    /**
     * Link to User entity for centralized authentication
     * Optional because OTP can be sent before user is fully identified
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

}
