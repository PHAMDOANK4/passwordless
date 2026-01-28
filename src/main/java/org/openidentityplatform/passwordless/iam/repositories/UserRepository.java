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

package org.openidentityplatform.passwordless.iam.repositories;

import org.openidentityplatform.passwordless.iam.models.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, String> {
    
    /**
     * Find user by email
     */
    Optional<User> findByEmail(String email);
    
    /**
     * Check if email exists
     */
    boolean existsByEmail(String email);
    
    /**
     * Find all users in a domain
     */
    @Query("SELECT u FROM User u WHERE u.domain.id = :domainId")
    Page<User> findByDomainId(@Param("domainId") String domainId, Pageable pageable);
    
    /**
     * Find active users in a domain
     */
    @Query("SELECT u FROM User u WHERE u.domain.id = :domainId AND u.status = 'ACTIVE'")
    Page<User> findActiveByDomainId(@Param("domainId") String domainId, Pageable pageable);
    
    /**
     * Count users in a domain
     */
    @Query("SELECT COUNT(u) FROM User u WHERE u.domain.id = :domainId")
    long countByDomainId(@Param("domainId") String domainId);
    
    /**
     * Find user by external ID (for SSO)
     */
    Optional<User> findByExternalId(String externalId);
}
