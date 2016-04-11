# GraylogPluginDatadog Plugin for Graylog

[![Build Status](https://travis-ci.org/https://github.com/underdogio/graylog-plugin-datadog.svg?branch=master)](https://travis-ci.org/https://github.com/underdogio/graylog-plugin-datadog)

Graylog plugin to output messages as a [Datadog](https://datadoghq.com/) event.

**Required Graylog version:** 2.0 and later

Installation
------------

[Download the plugin](https://github.com/https://github.com/underdogio/graylog-plugin-datadog/releases)
and place the `.jar` file in your Graylog plugin directory. The plugin directory
is the `plugins/` folder relative from your `graylog-server` directory by default
and can be configured in your `graylog.conf` file.

Restart `graylog-server` and you are done.

Development
-----------

You can improve your development experience for the web interface part of your plugin
dramatically by making use of hot reloading. To do this, do the following:

* `git clone https://github.com/Graylog2/graylog2-server.git`
* `cd graylog2-server/graylog2-web-interface`
* `ln -s $YOURPLUGIN plugin/`
* `npm install && npm start`

Usage
-----

This plugin adds the ability to add message outputs which create events in Datadog.

Requirements:

* Datadog API key - https://app.datadoghq.com/account/settings#api
* Datadog APP key - https://app.datadoghq.com/account/settings#api

To add a new event output, navigate to *System -> Outputs*, select the `Datadog output` type, and click `Launch new output`.

From this screen you can configure the output to add events to your [Datadog event stream](https://app.datadoghq.com/event/stream).

![](https://github.com/underdogio/graylog-plugin-datadog/blob/master/screenshot-settings.png)


Getting started
---------------

This project is using Maven 3 and requires Java 7 or higher.

* Clone this repository.
* Run `mvn package` to build a JAR file.
* Optional: Run `mvn jdeb:jdeb` and `mvn rpm:rpm` to create a DEB and RPM package respectively.
* Copy generated JAR file in target directory to your Graylog plugin directory.
* Restart the Graylog.

Plugin Release
--------------

We are using the maven release plugin:

```
$ mvn release:prepare
[...]
$ mvn release:perform
```

This sets the version numbers, creates a tag and pushes to GitHub. Travis CI will build the release artifacts and upload to GitHub automatically.
