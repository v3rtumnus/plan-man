package at.v3rtumnus.planman.entity;

import lombok.Data;

import javax.persistence.Entity;
import javax.persistence.Id;

@Entity
@Data
public class UserProfile {

    @Id
    private String username;
    private String password;
}
