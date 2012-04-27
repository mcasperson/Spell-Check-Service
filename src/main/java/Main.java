import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.ws.rs.core.PathSegment;

import net.htmlparser.jericho.Source;

import org.codehaus.jackson.map.ObjectMapper;
import org.jboss.resteasy.client.ProxyFactory;
import org.jboss.resteasy.plugins.providers.RegisterBuiltin;
import org.jboss.resteasy.specimpl.PathSegmentImpl;
import org.jboss.resteasy.spi.ResteasyProviderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

import com.redhat.ecs.commonutils.CollectionUtilities;
import com.redhat.ecs.commonutils.ExceptionUtilities;
import com.redhat.ecs.commonutils.XMLUtilities;
import com.redhat.ecs.servicepojo.ServiceStarter;
import com.redhat.topicindex.rest.collections.BaseRestCollectionV1;
import com.redhat.topicindex.rest.entities.StringConstantV1;
import com.redhat.topicindex.rest.entities.TopicV1;
import com.redhat.topicindex.rest.expand.ExpandDataDetails;
import com.redhat.topicindex.rest.expand.ExpandDataTrunk;
import com.redhat.topicindex.rest.sharedinterface.RESTInterfaceV1;

import dk.dren.hunspell.Hunspell;
import dk.dren.hunspell.Hunspell.Dictionary;

public class Main
{
	private static final String SPELL_CHECK_QUERY_SYSTEM_PROPERTY = "topicIndex.spellCheckQuery";
	private static final Integer DOCBOOK_IGNORE_ELEMENTS_STRING_CONSTANT_ID = 30;
	/** http://en.wikipedia.org/wiki/Regular_expression#POSIX_character_classes **/
	private static final String PUNCTUATION_CHARACTERS_RE = "[\\]\\[!\"#$%&'()*+,./:;<=>?@\\^_`{|}~\\-\\s]+";
	private final ObjectMapper mapper = new ObjectMapper();
	private final String query;

	public static void main(final String[] args)
	{
		final ServiceStarter starter = new ServiceStarter();
		if (starter.isValid())
		{
			RegisterBuiltin.register(ResteasyProviderFactory.getInstance());
			new Main(starter);
		}
	}

	public Main(final ServiceStarter serviceStarter)
	{
		query = System.getProperty(SPELL_CHECK_QUERY_SYSTEM_PROPERTY);

		try
		{
			final RESTInterfaceV1 restClient = ProxyFactory.create(RESTInterfaceV1.class, serviceStarter.getSkynetServer());

			final PathSegment pathSegment = new PathSegmentImpl(query, false);

			final ExpandDataTrunk expand = new ExpandDataTrunk();

			final ExpandDataTrunk topicsExpand = new ExpandDataTrunk(new ExpandDataDetails("topics"));
			expand.setBranches(CollectionUtilities.toArrayList(topicsExpand));

			final String expandString = mapper.writeValueAsString(expand);
			final String expandEncodedStrnig = URLEncoder.encode(expandString, "UTF-8");

			final BaseRestCollectionV1<TopicV1> topics = restClient.getJSONTopicsWithQuery(pathSegment, expandEncodedStrnig);
			final StringConstantV1 ignoreTags = restClient.getJSONStringConstant(DOCBOOK_IGNORE_ELEMENTS_STRING_CONSTANT_ID, "");
			final List<String> ignoreTagsList = CollectionUtilities.toArrayList(ignoreTags.getValue().split("\r\n"));

			final Dictionary standardDict = Hunspell.getInstance().getDictionary("target/classes/dict/en_US/en_US");
			final Dictionary customDict = Hunspell.getInstance().getDictionary("target/classes/customdict/en_US/en_US");

			for (final TopicV1 topic : topics.getItems())
			{
				processDocument(topic, ignoreTagsList, standardDict, customDict);
			}

		}
		catch (final Exception ex)
		{
			ExceptionUtilities.handleException(ex);
		}
	}

	/**
	 * Here we spell check a topic
	 * 
	 * @param topic
	 *            The topic to be spell checked
	 * @param ignoreElements
	 *            The list of elements that are to be ignored
	 */
	private void processDocument(final TopicV1 topic, final List<String> ignoreElements, final Dictionary standarddict, final Dictionary customDict)
	{
		final Document doc = XMLUtilities.convertStringToDocument(topic.getXml());
		stripOutIgnoredElements(doc, ignoreElements);
		final String cleanedXML = XMLUtilities.convertDocumentToString(doc, "UTF-8").replaceAll("\n", " ");

		/* render the XML to text using jericho */
		final Source source = new Source(cleanedXML);
		final String xmlText = source.getRenderer().toString();

		/* Get the word list */
		final List<String> xmlTextWords = CollectionUtilities.toArrayList(xmlText.split(PUNCTUATION_CHARACTERS_RE));

		final Map<String, List<String>> errors = new HashMap<String, List<String>>();
		final Map<String, Integer> errorCounts = new HashMap<String, Integer>();

		for (final String word : xmlTextWords)
		{
			if (!word.trim().isEmpty())
			{
				final boolean standardDictMispelled = standarddict.misspelled(word);
				final boolean customDictMispelled = customDict.misspelled(word);

				if (standardDictMispelled && customDictMispelled)
				{
					if (errors.containsKey(word))
					{
						errorCounts.put(word, errorCounts.get(word) + 1);
					}
					else
					{
						final List<String> suggestions = standarddict.suggest(word);
						CollectionUtilities.addAllThatDontExist(customDict.suggest(word), suggestions);
						Collections.sort(suggestions);
						
						errors.put(word,  suggestions);
						errorCounts.put(word, 1);
					}
				}
			}
		}

		if (errors.size() != 0)
		{
			for (final String word : errors.keySet())
			{
				System.out.print(word);
				if (errorCounts.get(word) != 1)
					System.out.print(" [x" + errorCounts.get(word) + "]");
				System.out.print(": ");
				System.out.println(CollectionUtilities.toSeperatedString(errors.get(word)));
			}
		}
	}

	/**
	 * Here we remove any nodes that we don't want to include in the spell check
	 * 
	 * @param node
	 *            The node to process
	 * @param ignoreElements
	 *            The list of elements that are to be ignored
	 */
	private void stripOutIgnoredElements(final Node node, final List<String> ignoreElements)
	{
		final List<Node> removeNodes = new ArrayList<Node>();

		for (int i = 0; i < node.getChildNodes().getLength(); ++i)
		{
			final Node childNode = node.getChildNodes().item(i);

			for (final String ignoreElement : ignoreElements)
			{
				if (childNode.getNodeName().toLowerCase().equals(ignoreElement.toLowerCase()))
				{
					removeNodes.add(childNode);
				}
			}
		}

		for (final Node removeNode : removeNodes)
		{
			node.removeChild(removeNode);
		}

		for (int i = 0; i < node.getChildNodes().getLength(); ++i)
		{
			final Node childNode = node.getChildNodes().item(i);
			stripOutIgnoredElements(childNode, ignoreElements);
		}
	}
}
