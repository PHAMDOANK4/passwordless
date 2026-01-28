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

import org.openidentityplatform.passwordless.iam.models.Domain;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DomainRepository extends JpaRepository<Domain, String> {
    
    /**
     * Find domain by domain name
     */
    Optional<Domain> findByDomainName(String domainName);
    
    /**
     * Check if domain name exists
     */
    boolean existsByDomainName(String domainName);
    
    /**
     * Find all active domains
     */
    Iterable<Domain> findByActiveTrue();
}
