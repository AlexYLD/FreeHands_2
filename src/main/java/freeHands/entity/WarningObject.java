package freeHands.entity;

import lombok.Getter;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Getter
public class WarningObject {
    private WarningType warningType;
    private List<ItuffObject> badItuffs;

    public WarningObject(WarningType warningType, ItuffObject... ituffs) {
        this.warningType = warningType;
        badItuffs = Arrays.stream(ituffs).collect(Collectors.toList());
    }

    @Override
    public String toString() {
        StringBuilder res = new StringBuilder(warningType.toString().replace("_", " ") + ": ");
        ItuffObject firstItuff = badItuffs.get(0);
        res.append(firstItuff.getFileName());
        if (warningType.equals(WarningType.DUPLICATES)) {
            res.append("(").append(firstItuff.getBin()).append(")");
        }
        res.append(" on ").append(firstItuff.getHost());
        if (badItuffs.size() > 1) {
            ItuffObject nextItuff;
            for (int i = 1; i < badItuffs.size(); i++) {
                nextItuff = badItuffs.get(i);
                res.append(" and ").append(nextItuff.getFileName())
                        .append("(").append(nextItuff.getBin()).append(")")
                        .append(" on ").append(nextItuff.getHost());
            }
        }
        return res.toString();
    }
}
