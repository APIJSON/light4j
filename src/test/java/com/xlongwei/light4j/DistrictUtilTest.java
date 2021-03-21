package com.xlongwei.light4j;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

import org.apache.commons.lang3.StringUtils;
import org.junit.Test;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.event.SyncReadListener;
import com.xlongwei.light4j.util.FileUtil;
import com.xlongwei.light4j.util.FileUtil.CharsetNames;
import com.xlongwei.light4j.util.FileUtil.LineHandler;
import com.xlongwei.light4j.util.FileUtil.TextReader;
import com.xlongwei.light4j.util.FileUtil.TextWriter;
import com.xlongwei.light4j.util.SqlInsert;
import com.xlongwei.light4j.util.StringUtil;

import cn.hutool.json.JSONSupport;
import cn.hutool.poi.excel.ExcelUtil;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

/**
 * 行政区划代码数据处理
 * @author xlongwei
 */
@Slf4j
public class DistrictUtilTest {
	@Data
	static class District {
		private String province;
		private String provinceName;
		private String city;
		private String cityName;
		private String county;
		private String countyName;
	}
	@Test
	public void parseData() throws Exception {
		Map<String, District> parseTxt = parseTxt();
		log.info("parse txt={}", parseTxt.size());
		Map<String, District> parseXlsx = parseXlsx();
		log.info("parse xlsx={}", parseXlsx.size());
		parseTxt.entrySet().forEach(entry -> {
			String county = entry.getKey();
			District district = parseXlsx.get(county);
			if(district==null) {
				District b = entry.getValue();
				if(b.getCounty().startsWith(b.getCity().substring(0, 4))) {
					log.info("add county={}, district=\n{}", county, b);
					parseXlsx.put(county, b);
				}else {
					log.error("bad district={}", b);
				}
			}else {
				if(!district.equals(entry.getValue())) {
					District b = entry.getValue();
					if(b.getCounty().startsWith(b.getCity().substring(0, 4))) {
						log.info("district not equal\n{}\n{}", district, entry.getValue());
					}
				}
			}
		});
		log.info("available districts={}", parseXlsx.size());
		SqlInsert sqlInsert = new SqlInsert("district");
		sqlInsert.addColumns("provinceName,province,cityName,city,countyName,county".split("[,]"));
		TextWriter writer = new TextWriter("apijson/district.sql", CharsetNames.UTF_8);
		int batch = 100;
		List<String> keys = new ArrayList<>(parseXlsx.keySet());
		Collections.sort(keys);
		for(String key : keys) {
			if(sqlInsert.size() >= batch) {
				writer.writeln(sqlInsert.toString());
				sqlInsert.clear();
			}
			District d = parseXlsx.get(key);
			sqlInsert.addValues(d.getProvinceName(), d.getProvince(), d.getCityName(), d.getCity(), d.getCountyName(), d.getCounty());
		}
		if(sqlInsert.size() > 0) {
			writer.writeln(sqlInsert.toString());
		}
		writer.close();
		log.info("finish");
	}
	private Map<String, District> parseTxt() throws Exception {
		Map<String, District> parse = new HashMap<>();
		new TextReader("apijson/district.txt", CharsetNames.UTF_8).handleLines(new LineHandler() {
			String province, provinceName, city, cityName;
			@Override
			public void handle(String line) {
				if(!StringUtil.isBlank(line)) {
					String[] split = StringUtils.split(line);
					if(split==null || split.length!=2) {
						log.error("bad line={}", line);
					}else {
						String county = split[0], countyName = split[1];
						if(!StringUtil.isNumbers(county)) {
							log.error("bad line={}", line);
						}else if(county.endsWith("0000")) {
							province = city = split[0];
							provinceName = cityName = split[1];
						}else if(county.endsWith("00")) {
							city = split[0];
							cityName = split[1];
						}else {
							District district = parse.get(county);
							if(district != null) {
								log.error("repeated county={}, countyName={}", county, countyName);
							}else {
								district = new District();
								district.setProvinceName(provinceName);
								district.setProvince(province);
								district.setCityName(cityName);
								district.setCity(city);
								district.setCountyName(countyName);
								district.setCounty(county);
								parse.put(county, district);
							}
						}
					}
				}
			}
		});
		return parse;
	}
	private Map<String, District> parseXlsx() throws Exception {
		Map<String, District> parse = new HashMap<>();
		ExcelUtil.readBySax(new BufferedInputStream(new FileInputStream("apijson/district.xlsx")), 0,
				(int i, long j, List<Object> list) -> {
						Object[] array = list.toArray();
						if(array!=null && array.length==6) {
							String county = Objects.toString(array[5], null);
							if(StringUtil.isNumbers(county)) {
								District district = parse.get(county);
								if(district != null) {
									log.error("repeated county={}, row={}", county, j);
								}else {
									int idx = 0;
									district = new District();
									district.setProvinceName(Objects.toString(array[idx++], ""));
									district.setProvince(Objects.toString(array[idx++], ""));
									district.setCityName(Objects.toString(array[idx++], ""));
									district.setCity(Objects.toString(array[idx++], ""));
									district.setCountyName(Objects.toString(array[idx++], ""));
									district.setCounty(Objects.toString(array[idx++], ""));
									parse.put(county, district);
								}
							}
						}
				});
		return parse;
	}
	
	@Test
	public void parseCounty() throws Exception {
		SyncReadListener listener = new SyncReadListener();
		EasyExcel.read(new BufferedInputStream(new FileInputStream("apijson/city_define.xls"))).autoCloseStream(true).registerReadListener(listener).sheet().doRead();
		SqlInsert sqlInsert = new SqlInsert("pc_county");
		sqlInsert.addColumns("county_code,county_name,sup_city_code".split("[,]"));
		Set<String> countyCodes = new HashSet<>();
		for(Object obj : listener.getList()) {
			Map<Integer, String> map = (Map<Integer, String>)obj;
			String countyCode = Objects.toString(map.get(1));
			String countyName = Objects.toString(map.get(2));
			if(countyCode==null || countyCode.length()!=6) {
				continue;
			}
			if(countyCodes.contains(countyCode)) {
				continue;
			}
			countyCodes.add(countyCode);
			String supCityCode = countyCode.substring(0, 4)+"00";
			sqlInsert.addValues(countyCode, countyName, supCityCode);
		}
		List<String> sqls = sqlInsert.sqls();
		for(String sql : sqls) {
			System.out.println(sql);
		}
	}
	
	@Getter
	@Setter
	@AllArgsConstructor
	static class PcCounty extends JSONSupport {
		private String code, name, supCode;
	}
	@Test
	public void compareCounty() throws Exception {
		String dir = "../Servers/library/pc_county";
		Map<String, PcCounty> codeMap = new TreeMap<>();
		readFile(new File(dir, "pc_county.txt"), codeMap);
		
		SqlInsert citySql = new SqlInsert("pc_city").addColumns("city_code,city_name,sup_province_code".split("[,]"));
		SqlInsert countySql = new SqlInsert("pc_county").addColumns("county_code,county_name,sup_city_code".split("[,]"));
		for(String code : codeMap.keySet()) {
			PcCounty pcCounty = codeMap.get(code);
			if(code.endsWith("00")) {
				if(code.endsWith("0000")) {
					citySql.addValues(code, pcCounty.getName(), code.substring(0, 2)+"0000");
					countySql.addValues(code, pcCounty.getName(), pcCounty.supCode);
				}else {
					citySql.addValues(code, pcCounty.getName(), code.substring(0, 2)+"0000");
				}
			}else {
				countySql.addValues(code, pcCounty.getName(), pcCounty.supCode);
			}
		}
		TextWriter writer = FileUtil.writer(new File(dir, "city.sql"), CharsetNames.UTF_8);
		citySql.sqls().forEach(writer::writeln);
		writer.close();
		writer = FileUtil.writer(new File(dir, "county.sql"), CharsetNames.UTF_8);
		countySql.sqls().forEach(writer::writeln);
		writer.close();
	}
	private void readFile(File file, Map<String, PcCounty> codeMap) {
		List<String> lines = FileUtil.readLines(file, CharsetNames.UTF_8);
		for(String line:lines) {
			if(line==null || line.isEmpty() || !line.contains("	")) {
				continue;
			}
			String[] split=line.split("	");
			String countyCode = split[0];
			String countyName = split[1];
			String supCountyCode = split[2];
			PcCounty pcCounty = new PcCounty(countyCode, countyName, supCountyCode);
			codeMap.put(countyCode, pcCounty);
		}
	}
}
