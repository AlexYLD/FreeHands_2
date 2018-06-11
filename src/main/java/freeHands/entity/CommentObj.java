//Created by Alexey Yarygin
package freeHands.entity;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@NoArgsConstructor
public class CommentObj extends ItuffObject {
    public CommentObj(String comment, String host) {
        setBin(comment);
        setFileName("comment");
        setDate(new Date(System.currentTimeMillis()));
        setHost(host);
    }
}
