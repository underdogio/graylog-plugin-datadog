package io.underdog;

import com.floreysoft.jmte.Engine;
import com.google.common.base.Joiner;
import com.google.common.collect.Maps;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import org.apache.commons.lang3.StringUtils;
import org.glassfish.jersey.client.authentication.HttpAuthenticationFeature;
import org.graylog2.plugin.Message;
import org.graylog2.plugin.configuration.Configuration;
import org.graylog2.plugin.configuration.ConfigurationRequest;
import org.graylog2.plugin.configuration.fields.ConfigurationField;
import org.graylog2.plugin.configuration.fields.TextField;
import org.graylog2.plugin.outputs.MessageOutput;
import org.graylog2.plugin.outputs.MessageOutputConfigurationException;
import org.graylog2.plugin.streams.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class GraylogPluginDatadog implements MessageOutput {
    private static final Logger LOG = LoggerFactory.getLogger(GraylogPluginDatadog.class);

    private Configuration configuration;
    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    private WebTarget eventTarget;
    private String[] tags;
    private String priority;
    private String alertType;
    private String aggregationKey;
    private URI eventUrl;

    private static final String CK_DATADOG_API_KEY = "datadog_api_key";
    private static final String CK_DATADOG_APP_KEY = "datadog_app_key";
    private static final String CK_DATADOG_TAGS = "datadog_tags";
    private static final String CK_DATADOG_PRIORITY = "datadog_priority";
    private static final String CK_DATADOG_ALERT_TYPE = "datadog_alert_type";
    private static final String CK_DATADOG_AGGREGATION_KEY = "datadog_aggregation_KEY";
    private static final String DATADOG_API_URL = "https://app.datadoghq.com/api/v1/events";

    @Inject
    public GraylogPluginDatadog(@Assisted Stream stream, @Assisted Configuration configuration) throws MessageOutputConfigurationException {
        this.configuration = configuration;

        tags = configuration.getString(CK_DATADOG_TAGS, "").split(",");
        priority = configuration.getString(CK_DATADOG_PRIORITY, "normal");
        alertType = configuration.getString(CK_DATADOG_ALERT_TYPE, "info");
        aggregationKey = configuration.getString(CK_DATADOG_AGGREGATION_KEY, "");

        try {
            eventUrl = new URI(String.format("%s?api_key=%s&app_key=%s",
                                             DATADOG_API_URL,
                                             configuration.getString(CK_DATADOG_API_KEY),
                                             configuration.getString(CK_DATADOG_APP_KEY)));
        } catch (URISyntaxException e){
            throw new MessageOutputConfigurationException("Syntax error in datadog event URL");
        }

        Client client = ClientBuilder.newClient();
        eventTarget = client.target(eventUrl);
        isRunning.set(true);
    }

    @Override
    public boolean isRunning() {
        return isRunning.get();
    }

    public String getMessageText(Message message) {
        final StringBuilder sb = new StringBuilder();
        sb.append("%%% \n");

        final Map<String, Object> filteredFields = Maps.newHashMap(message.getFields());
        for (Map.Entry<String, Object> entry : filteredFields.entrySet()) {
            sb.append("**").append(entry.getKey()).append("**: ").append(entry.getValue().toString()).append("\n\n");
        }

        sb.append("\n %%%");
        return sb.toString();
    }

    public String getMessageTitle(Message message) {
        final StringBuilder sb = new StringBuilder();
        sb.append("graylog: ");
        if (message.getField("facility") != null) {
            sb.append(message.getField("facility")).append(" ");
        }
        sb.append("[").append(message.getSource()).append("] ");
        sb.append(message.getMessage());
        return sb.toString();
    }

    @Override
    public void write(Message message) throws Exception {
        HashMap<String, Object> payload = new HashMap<>();
        payload.put("title", getMessageTitle(message));
        payload.put("text", getMessageText(message));
        payload.put("tags", tags);
        payload.put("source_type_name", "graylog");
        payload.put("priority", priority);
        payload.put("alert_type", alertType);

        if (aggregationKey != "") {
            payload.put("aggregation_key", aggregationKey);
        } else {
            final StringBuilder sb = new StringBuilder();
            sb.append(message.getSource());
            if (message.getField("facility") != null) {
                sb.append("_").append(message.getField("facility"));
            }
            payload.put("aggregation_key", sb.toString());
        }

        Response response = eventTarget
            .request(MediaType.APPLICATION_JSON)
            .post(Entity.entity(payload, MediaType.APPLICATION_JSON), Response.class);

        String result = String.format(
                "POST [%s] to [%s], status code [%d], headers: %s, returned data: %s",
                payload, eventUrl, response.getStatus(),
                response.getHeaders(), response);
        if (response.getStatus() >= 400) {
            LOG.info(result);
        } else {
            LOG.debug(result);
        }
    }

    @Override
    public void write(List<Message> messages) throws Exception {
        for (Message message : messages) {
            write(message);
        }
    }

    @Override
    public void stop() {
        LOG.info("Stopping Datadog output");
        isRunning.set(false);
    }

    public interface Factory extends MessageOutput.Factory<GraylogPluginDatadog> {
        @Override
        GraylogPluginDatadog create(Stream stream, Configuration configuration);

        @Override
        Config getConfig();

        @Override
        Descriptor getDescriptor();
    }

    public static class Config extends MessageOutput.Config {
        @Override
        public ConfigurationRequest getRequestedConfiguration() {
            final ConfigurationRequest configurationRequest = new ConfigurationRequest();

            configurationRequest.addField(new TextField(
                            CK_DATADOG_API_KEY, "Datadog API key", "",
                            "API key for Datadog",
                            ConfigurationField.Optional.NOT_OPTIONAL)
            );

            configurationRequest.addField(new TextField(
                            CK_DATADOG_APP_KEY, "Datadog APP key", "",
                            "APP key for Datadog",
                            ConfigurationField.Optional.NOT_OPTIONAL)
            );

            configurationRequest.addField(new TextField(
                            CK_DATADOG_TAGS, "Datadog event tags", "",
                            "Comma separated list of tags to add to the event, e.g. 'tag:value,name,another:tag'",
                            ConfigurationField.Optional.OPTIONAL)
            );

            configurationRequest.addField(new TextField(
                            CK_DATADOG_PRIORITY, "Datadog event priority", "",
                            "Datadog event priority, one of ('normal', or 'low') [default: 'normal']",
                            ConfigurationField.Optional.OPTIONAL)
            );

            configurationRequest.addField(new TextField(
                            CK_DATADOG_ALERT_TYPE, "Datadog event alert type", "",
                            "Datadog event alert type, one of ('error', 'warning', 'success', or 'info') [default: 'info']",
                            ConfigurationField.Optional.OPTIONAL)
            );

            configurationRequest.addField(new TextField(
                            CK_DATADOG_AGGREGATION_KEY, "Datadog event aggregation key", "",
                            "Datadog event custom aggregation key [default: '[source]_[facility]']",
                            ConfigurationField.Optional.OPTIONAL)
            );

            return configurationRequest;
        }
    }

    public static class Descriptor extends MessageOutput.Descriptor {
        public Descriptor() {
            super("Datadog output", false, "", "An output plugin that adds log data as events to Datadog");
        }
    }
}
