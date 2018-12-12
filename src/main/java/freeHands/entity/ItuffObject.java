//Created by Alexey Yarygin
package freeHands.entity;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Data
@NoArgsConstructor
public class ItuffObject implements Comparable<ItuffObject> {
    private String fileName;
    private Map<String, String> ult;
    private String strUlt;
    private String bin;
    private String lot;
    private String sum;
    private String host;
    private String location;
    private Date date;
    private String test;
    private boolean isGolden;


    public ItuffObject(String fileName, String ituffText) {
        ult = new HashMap<>();
        this.fileName = fileName;
        ult.put("lot", getValueFrom(ituffText, "_trlot_"));
        ult.put("wafer", getValueFrom(ituffText, "_trwafer_"));
        ult.put("x", getValueFrom(ituffText, "_trxloc_"));
        ult.put("y", getValueFrom(ituffText, "_tryloc_"));
        StringBuffer sbUlt = new StringBuffer();
        for (String key : ult.keySet()) {
            sbUlt = sbUlt.append(ult.get(key)).append(";");
        }
        strUlt = sbUlt.toString();
        if (strUlt.contains("not found")) {
            strUlt = "No ULT";
        }
        bin = getBin(ituffText);
        lot = getValueFrom(ituffText, "_lotid_");
        sum = getValueFrom(ituffText, "_smrynam_");
        host = getValueFrom(ituffText, "_sysid_");
        test = getValueFrom(ituffText, "_prgnm_");
        date = getDateValue(ituffText);
        location = getValueFrom(ituffText, "_lcode_");

        isGolden = getIfGolden(ituffText);/*(ituffText, "_comnt_").equalsIgnoreCase("regular_unit_test"));*/
    }

    private boolean getIfGolden(String ituffText) {
        String res;
        String search = "_comnt_";
        int startIndex = ituffText.lastIndexOf(search) + search.length();
        if (startIndex == -1 + search.length()) {
            return false;
        }
        res = ituffText.substring(startIndex, ituffText.indexOf("\n", startIndex));

        return res.equalsIgnoreCase("golden_unit_test");
    }

    @SneakyThrows
    private Date getDateValue(String ituffText) {
        SimpleDateFormat sdf;
        String dateStr;
        String searchDate = getValueFrom(ituffText, "_enddate_");
        if (!searchDate.equals("not found")) {
            sdf = new SimpleDateFormat("yyyyMMddHHmmss");
            dateStr = searchDate.substring(0, 14);
            return sdf.parse(dateStr);
        } else {
            sdf = new SimpleDateFormat("ddMMyyyyHHmmss");
            dateStr = fileName.substring(5, fileName.lastIndexOf("_")).replaceAll("_", "");
            try {
                return sdf.parse(dateStr);
            } catch (ParseException e) {
                return new Date();
            }
        }
    }

    private String getBin(String ituffText) {
        String fbin = getValueFrom(ituffText, "_curfbin_");
        String ibin = getValueFrom(ituffText, "_curibin_");
        String bin = ibin + "." + fbin;

        if (bin.equals("1.100") || bin.equals("1.0")) {
            bin = "PASS";
        }
        return bin;
    }

    private String getValueFrom(String ituffText, String search) {
        String res;
        int startIndex = ituffText.indexOf(search) + search.length();
        if (startIndex == -1 + search.length()) {
            return "not found";
        }
        res = ituffText.substring(startIndex, ituffText.indexOf("\n", startIndex));

        return res;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ItuffObject that = (ItuffObject) o;

        return (strUlt != null && !strUlt.equals("No ULT")) && strUlt.equals(that.strUlt);
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (strUlt != null ? strUlt.hashCode() : 0);
        return result;
    }

    @Override
    public int compareTo(ItuffObject ituffObject) {
        return (date != null && ituffObject.getDate() != null) ? this.date.compareTo(ituffObject.getDate()) : -1;
    }
}
