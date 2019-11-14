package xyz.anythings.base.query.store;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import net.sf.common.util.ResourceUtils;
import xyz.anythings.base.query.IQueryPathHelper;
import xyz.elidom.exception.server.ElidomRuntimeException;
import xyz.elidom.sys.SysConstants;
import xyz.elidom.util.ValueUtil;

/**
 * 쿼리 스토어 추상 클래스
 * - 기본 데이터베이스 유형, 기본 쿼리 경로, 쿼리 캐쉬 관리
 * 
 * @author shortstop
 */
public class AbstractQueryStore implements IQueryPathHelper {

	/**
	 * 데이터베이스 유형 
	 */
	protected String databaseType;
	/**
	 * 쿼리 기본 경로
	 */
	protected String basePath;
	/**
	 * Ansi 쿼리 기본 경로
	 */
	protected String defaultBasePath;
	/**
	 * SQL 캐쉬
	 */
	protected Map<String, String> sqlCache = new HashMap<String, String>();
	
	@Override
	public void initQueryStore(String databaseType) {
		this.databaseType = databaseType;
		this.basePath = "xyz/anythings/base/query/" + this.databaseType + SysConstants.SLASH;
		this.defaultBasePath = "xyz/anythings/base/query/ansi/"; 
	}

	@Override
	public String getDatabaseType() {
		return this.databaseType;
	}

	@Override
	public String getBasePath() {
		return this.basePath;
	}

	@Override
	public String getQueryByPath(String path) {
		// 1. 쿼리 캐쉬에서 체크해서 있다면 찾아서 리턴
		if(this.sqlCache.containsKey(path)) {
			return this.sqlCache.get(path);
			
		// 2. 없으면 path로 부터 쿼리를 찾아서 쿼리 캐쉬에 추가 
		} else {
			String fullpath = this._getFullQueryPath(this.basePath, path, false);
			String sql = this._getQueryByPath(fullpath);
			sqlCache.put(path, sql);
			return sql;
		}
	}
	
	/**
	 * basePath, path로 Full Query Path를 생성하여 리턴
	 * 
	 * @param basePath
	 * @param path
	 * @param exceptionWhenNotFound
	 * @return
	 */
	protected String _getFullQueryPath(String basePath, String path, boolean exceptionWhenNotFound) {
		String fullpath = basePath + path + ".sql";
		
		if (ResourceUtils.exists(fullpath)) {
			return fullpath;
		} else {
			if(exceptionWhenNotFound) {
				throw new ElidomRuntimeException("Invalid query path [" + path + "]");
			} else {
				return this._getFullQueryPath(this.defaultBasePath, path, true);
			}
		}		
	}
	
	/**
	 * path로 쿼리를 조회하여 리턴
	 * 
	 * @param fullpath
	 * @return
	 */
	protected String _getQueryByPath(String fullpath) {
		String ql = null;
		
		try {
			ql = ResourceUtils.readText(fullpath);
		} catch (IOException e) {
			throw new ElidomRuntimeException("Failed to get query by path", e.getMessage());
		}
		
		if(ValueUtil.isEmpty(ql)) {
			throw new ElidomRuntimeException("Query of path [" + fullpath + "] is empty");
		}
		
		return ql;
	}

}
