/*
 * Copyright (C) 2010 Geometer Plus <contact@geometerplus.com>
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA
 * 02110-1301, USA.
 */

package org.geometerplus.fbreader.network.opds;

import java.util.*;
import java.net.URLConnection;
import java.io.InputStream;
import java.io.IOException;

import org.geometerplus.zlibrary.core.util.ZLNetworkUtil;
import org.geometerplus.zlibrary.core.network.ZLNetworkRequest;

import org.geometerplus.fbreader.network.*;
import org.geometerplus.fbreader.network.authentication.NetworkAuthenticationManager;


class OPDSLink extends AbstractNetworkLink {

	public interface FeedCondition {
		int REGULAR = 0;
		int NEVER = 1;
		int SIGNED_IN = 2;
	}

	private TreeMap<RelationAlias, String> myRelationAliases;

	private TreeMap<String, Integer> myUrlConditions;
	private LinkedList<URLRewritingRule> myUrlRewritingRules;
	private NetworkAuthenticationManager myAuthenticationManager;

	private final boolean myHasStableIdentifiers;

	OPDSLink(String siteName, String title, String summary, String icon,
			Map<String, String> links, boolean hasStableIdentifiers) {
		super(siteName, title, summary, icon, links);
		myHasStableIdentifiers = hasStableIdentifiers;
	}

	final void setRelationAliases(Map<RelationAlias, String> relationAliases) {
		if (relationAliases != null && relationAliases.size() > 0) {
			myRelationAliases = new TreeMap<RelationAlias, String>(relationAliases);
		} else {
			myRelationAliases = null;
		}
	}

	final void setUrlConditions(Map<String, Integer> conditions) {
		if (conditions != null && conditions.size() > 0) {
			myUrlConditions = new TreeMap<String, Integer>(conditions);
		} else {
			myUrlConditions = null;
		}
	}

	final void setUrlRewritingRules(List<URLRewritingRule> rules) {
		if (rules != null && rules.size() > 0) {
			myUrlRewritingRules = new LinkedList<URLRewritingRule>(rules);
		} else {
			myUrlRewritingRules = null;
		}
	}

	final void setAuthenticationManager(NetworkAuthenticationManager mgr) {
		myAuthenticationManager = mgr;
	}

	ZLNetworkRequest createNetworkData(String url, final OPDSCatalogItem.State result) {
		if (url == null) {
			return null;
		}
		url = rewriteUrl(url, false);
		return new ZLNetworkRequest(url) {
			@Override
			public String handleStream(URLConnection connection, InputStream inputStream) throws IOException {
				if (result.Listener.confirmInterrupt()) {
					return null;
				}

				new OPDSXMLReader(
					new NetworkOPDSFeedReader(URL, result)
				).read(inputStream);

				if (result.Listener.confirmInterrupt()) {
					if (!myHasStableIdentifiers && result.LastLoadedId != null) {
						// If current catalog doesn't have stable identifiers
						// and catalog wasn't completely loaded (i.e. LastLoadedIdentifier is not null)
						// then reset state to load current page from the beginning 
						result.LastLoadedId = null;
					} else {
						result.Listener.commitItems(OPDSLink.this);
					}
				} else {
					result.Listener.commitItems(OPDSLink.this);
				}
				return null;
			}
		};
	}

	private final String searchURL(String query) {
		final String url = getLink(URL_SEARCH);
		final String searchTerms = "{searchTerms}";
		if (url.contains(searchTerms)) {
			return url.replace(searchTerms, query);
		}
		return url.replace("%s", query);
	}

	@Override
	public OPDSCatalogItem.State createOperationData(INetworkLink link,
			NetworkOperationData.OnNewItemListener listener) {
		return new OPDSCatalogItem.State(link, listener);
	}

	public ZLNetworkRequest simpleSearchRequest(String pattern, NetworkOperationData data) {
		if (getLink(URL_SEARCH) == null) {
			return null;
		}
		return createNetworkData(
			searchURL(ZLNetworkUtil.htmlEncode(pattern)),
			(OPDSCatalogItem.State) data
		);
	}

	public ZLNetworkRequest resume(NetworkOperationData data) {
		return createNetworkData(data.ResumeURI, (OPDSCatalogItem.State) data);
	}

	public NetworkLibraryItem libraryItem() {
		TreeMap<Integer, String> urlMap = new TreeMap<Integer, String>();
		urlMap.put(NetworkCatalogItem.URL_CATALOG, getLink(URL_MAIN));
		return new OPDSCatalogItem(this, getTitle(), getSummary(), getIcon(), urlMap);
	}

	public NetworkAuthenticationManager authenticationManager() {
		return myAuthenticationManager;
	}

	public String rewriteUrl(String url, boolean isUrlExternal) {
		if (myUrlRewritingRules == null) {
			return url;
		}
		for (URLRewritingRule rule: myUrlRewritingRules) {
			if (rule.Apply != URLRewritingRule.APPLY_ALWAYS) {
				if ((rule.Apply == URLRewritingRule.APPLY_EXTERNAL && !isUrlExternal)
					|| (rule.Apply == URLRewritingRule.APPLY_INTERNAL && isUrlExternal)) {
					continue;
				}
			}
			switch (rule.Type) {
			case URLRewritingRule.ADD_URL_PARAMETER:
				url = ZLNetworkUtil.appendParameter(url, rule.Name, rule.Value);
				break;
			}
		}
		return url;
	}

	int getCondition(String url) {
		if (myUrlConditions == null) {
			return FeedCondition.REGULAR;
		}
		Integer cond = myUrlConditions.get(url);
		if (cond == null) {
			return FeedCondition.REGULAR;
		}
		return cond.intValue();
	}

	// rel and type must be either null or interned String objects.
	String relation(String rel, String type) {
		if (myRelationAliases == null) {
			return rel;
		}
		RelationAlias alias = new RelationAlias(rel, type);
		String mapped = myRelationAliases.get(alias);
		if (mapped != null) {
			return mapped;
		}
		if (type != null) {
			alias = new RelationAlias(rel, null);
			mapped = myRelationAliases.get(alias);
			if (mapped != null) {
				return mapped;
			}
		}
		return rel;
	}
}
