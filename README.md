# Alexa Go Vote Skill Lambda

This project provides a Lambda for an Alexa Skill to query for Election Information.
Currently it only supports getting Polling Place information with the activation
phrase of "Where do I vote?". It will then proceed to gather the registered street
address and zip code and use those to query the Civic Info API and return the first
polling place as a spoken place name and address. In future improvements we plan
to return a Card to the user's Alexa app with links to Get To The Polls and could
also expand to early vote site and drop box locations, in addition to ballot info.

## Prerequisites

You will need to set up [Amazon Web Services CLI](http://docs.aws.amazon.com/cli/latest/userguide/installing.html) on your machine.   All Alexa Skills are hosted out of the US-EAST-1, so you will want to set your configuration accordingly.

You will also need [Leiningen](https://leiningen.org/)

## Configuration

There are a couple of environment variables that the application needs:

`CIVIC_API_KEY`: the access key for the Civic Info API.
`PRODUCTION_DATA_ONLY`: true|false whether the API should only return production data.
`DEBUG`: set to true to have debug logging output turned on, defaults to false.

These environment vars will get pushed up the the Lambda function's environment. The
way to configure them is on the command line like:

```CIVIC_API_KEY=... PRODUCTION_DATA_ONLY=false lein cljs-lambda deploy```

## Deploying

Run `lein cljs-lambda default-iam-role` if you don't have yet have suitable
execution role to place in your project file.  This command will create an IAM
role under your default (or specified) AWS CLI profile, and modify your project
file to specify it as the execution default.

Otherwise, add an IAM role ARN under the function's `:role` key in the
`:functions` vector of your profile file, or in `:cljs-lambda` -> `:defaults` ->
`:role`.

Then:

```sh
$ CIVIC_API_KEY=... PRODUCTION_DATA_ONLY=true lein cljs-lambda deploy
```

## Configure

The Lambda needs a bit of configuration to work. You need to add the Alexa Skill Kit
to it, which is pretty simple click operation.

Also you need to configure an environment variable named `CIVIC_API_KEY` with the
key it will use to call the Civic Info API. Make sure to `Save` after, it was easy
to miss this the first time around.

## Logging

In addition to the Alexa Skill Kit, you should also configure a CloudWatch Logs plugin too
(also just a point and click affair), and when the code calls

`(.log js/console "Some message")`

This will show up in the CloudWatch Logs. This is a good way to debug things in situ
as there are limited ways to test the whole thing.
