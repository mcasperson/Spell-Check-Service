package com.redhat.topicindex.syntaxchecker;

import java.io.IOException;

import javax.ws.rs.Produces;
import javax.ws.rs.ext.ContextResolver;
import javax.ws.rs.ext.Provider;
import javax.ws.rs.core.MediaType;

import org.codehaus.jackson.JsonGenerationException;
import org.codehaus.jackson.map.JsonMappingException;
import org.codehaus.jackson.map.ObjectMapper;

import com.redhat.topicindex.rest.entities.mixins.JacksonMixins;

@Provider
@Produces(MediaType.APPLICATION_JSON)
public class JacksonContextResolver implements ContextResolver<ObjectMapper>
{

	private ObjectMapper objectMapper;

	public JacksonContextResolver() throws JsonGenerationException, JsonMappingException, IOException
	{
		objectMapper = new ObjectMapper();
		objectMapper.registerModule(JacksonMixins.initialise());
	}

	public ObjectMapper getContext(Class<?> type)
	{
		return objectMapper;
	}
}
