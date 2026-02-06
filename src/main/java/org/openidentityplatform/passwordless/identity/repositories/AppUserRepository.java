package org.openidentityplatform.passwordless.identity.repositories;

import org.openidentityplatform.passwordless.identity.models.AppUser;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface AppUserRepository extends CrudRepository<AppUser, UUID> {

    Optional<AppUser> findByHandle(String handle);

    Optional<AppUser> findByMailAddr(String mailAddr);

    Optional<AppUser> findByPhoneNum(String phoneNum);
}
