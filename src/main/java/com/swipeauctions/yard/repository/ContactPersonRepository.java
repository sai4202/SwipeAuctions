package com.swipeauctions.yard.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import com.swipeauctions.yard.entity.ContactPerson;

import java.util.UUID;

@Repository
public interface ContactPersonRepository extends JpaRepository<ContactPerson, UUID> {

}