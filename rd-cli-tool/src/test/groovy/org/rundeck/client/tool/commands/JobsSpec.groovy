/*
 * Copyright 2017 Rundeck, Inc. (http://rundeck.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.rundeck.client.tool.commands

import org.rundeck.client.api.model.Simple
import org.rundeck.client.api.model.scheduler.ForecastJobItem
import org.rundeck.client.testing.MockRdTool
import org.rundeck.client.tool.CommandOutput
import org.rundeck.client.tool.InputError


import okhttp3.MediaType
import okhttp3.ResponseBody
import org.rundeck.client.api.RundeckApi
import org.rundeck.client.api.model.DeleteJobsResult
import org.rundeck.client.api.model.ImportResult
import org.rundeck.client.api.model.JobItem
import org.rundeck.client.api.model.JobLoadItem
import org.rundeck.client.api.model.scheduler.ScheduledJobItem
import org.rundeck.client.tool.RdApp
import org.rundeck.client.tool.extension.RdTool
import org.rundeck.client.tool.options.JobFileOptions
import org.rundeck.client.tool.options.JobIdentOptions
import org.rundeck.client.tool.options.JobListOptions
import org.rundeck.client.tool.options.JobLoadOptions
import org.rundeck.client.tool.options.JobOutputFormatOption
import org.rundeck.client.tool.options.PagingResultOptions
import org.rundeck.client.tool.options.ProjectNameOptions
import org.rundeck.client.tool.options.VerboseOption
import org.rundeck.client.util.Client
import org.rundeck.client.util.RdClientConfig
import retrofit2.Retrofit
import retrofit2.mock.Calls
import spock.lang.Specification
import spock.lang.Unroll

/**
 * @author greg
 * @since 12/13/16
 */
class JobsSpec extends Specification {
    File tempFile = File.createTempFile('data', 'out')

    def setup() {
        tempFile = File.createTempFile('data', 'out')
    }

    def cleanup() {
        if (tempFile.exists()) {
            tempFile.delete()
        }
    }

    private RdTool setupMock(RundeckApi api, int apiVersion=18) {
        def retrofit = new Retrofit.Builder().baseUrl('http://example.com/fake/').build()
        def client = new Client(api, retrofit, null, null, apiVersion, true, null)
        def rdapp = Mock(RdApp) {
            getClient() >> client
            getAppConfig() >> Mock(RdClientConfig)
        }
        def rdTool = new MockRdTool(client: client, rdApp: rdapp)
        rdTool.appConfig = Mock(RdClientConfig)
        rdTool
    }

    def "job list with input parameters"() {
        given:
        def api = Mock(RundeckApi)
        RdTool rdTool = setupMock(api)
        def out = Mock(CommandOutput)
        Jobs command = new Jobs()
        command.rdTool = rdTool
        command.rdOutput = out

        def opts = new JobListOptions()
        opts.project = 'ProjectName'
        opts.jobExact = jobexact
        opts.groupExact = groupexact
        opts.setJob(job)
        opts.setGroup(group)

        when:
        command.list(new JobOutputFormatOption(), new JobFileOptions(), opts)

        then:
        1 * api.listJobs('ProjectName', job, group, jobexact, groupexact) >>
                Calls.response([new JobItem(id: 'fakeid')])
        0 * api._(*_)

        where:
        job  | group | jobexact | groupexact
        'a'  | 'b/c' | null     | null
        null | null  | 'a'      | 'b/c'
    }

    def "job list write to file with input parameters"() {
        given:
        def api = Mock(RundeckApi)
        RdTool rdTool = setupMock(api)
        def out = Mock(CommandOutput)
        Jobs command = new Jobs()
        command.rdTool = rdTool
        command.rdOutput = out

        def opts = new JobListOptions()
        opts.project = 'ProjectName'
        opts.jobExact = jobexact
        opts.groupExact = groupexact
        opts.setJob(job)
        opts.setGroup(group)


        def fileOptions = new JobFileOptions(
                format: JobFileOptions.Format.xml,
                file: tempFile
        )
        when:
        command.list(new JobOutputFormatOption(), fileOptions, opts)

        then:
        1 * api.exportJobs('ProjectName', job, group, jobexact, groupexact, 'xml') >>
                Calls.response(ResponseBody.create(MediaType.parse('application/xml'), 'abc'))
        0 * api._(*_)
        tempFile.exists()
        tempFile.text == 'abc'

        where:
        job  | group | jobexact | groupexact
        'a'  | 'b/c' | null     | null
        null | null  | 'a'      | 'b/c'
    }

    @Unroll
    def "jobs #action behavior"() {
        given:
        def deets = [
                enable    : "jobExecutionEnable",
                disable   : "jobExecutionDisable",
                reschedule: "jobScheduleEnable",
                unschedule: "jobScheduleDisable"
        ]
        def apiCall = deets[action]

        def api = Mock(RundeckApi)
        RdTool rdTool = setupMock(api)
        def out = Mock(CommandOutput)
        Jobs command = new Jobs()
        command.rdTool = rdTool
        command.rdOutput = out

        def opts = new JobIdentOptions(
                id: '123',
                project: 'testProj'
        )
        when:
        command."$action"(opts)

        then:
        1 * api."$apiCall"('123') >> Calls.response(new Simple(success: true))
        0 * api._(*_)

        where:
        action       | _
        'enable'     | _
        'disable'    | _
        'unschedule' | _
        'reschedule' | _
    }


    @Unroll
    def "job purge with job #job group #group jobexact #jobexact groupexact #groupexact"() {
        given:
        def api = Mock(RundeckApi)
        RdTool rdTool = setupMock(api)
        def out = Mock(CommandOutput)
        Jobs command = new Jobs()
        command.rdTool = rdTool
        command.rdOutput = out

        def opts = new Jobs.Purge()
        opts.confirm = true
        def listOpts = new JobListOptions()
        listOpts.setJob(job)
        listOpts.setGroup(group)
        listOpts.setJobExact(jobexact)
        listOpts.setGroupExact(groupexact)
        listOpts.setProject 'ProjectName'

        when:
        def result = command.purge(opts, new JobOutputFormatOption(), new JobFileOptions(), listOpts)

        then:
        1 * api.listJobs('ProjectName', job, group, jobexact, groupexact) >>
                Calls.response([new JobItem(id: 'fakeid')])
        1 * api.deleteJobsBulk({ it.ids == ['fakeid'] }) >> Calls.response(new DeleteJobsResult(allsuccessful: true))
        0 * api._(*_)
        result

        where:
        job  | group | jobexact | groupexact
        'a'  | null  | null     | null
        'a'  | 'b/c' | null     | null
        null | 'b/c' | null     | null
        null | null  | 'a'      | null
        null | null  | 'a'      | 'b/c'
        null | null  | null     | 'b/c'
    }
    @Unroll
    def "job purge with with batchsize"() {
        given:

        def api = Mock(RundeckApi)
        RdTool rdTool = setupMock(api)
        def out = Mock(CommandOutput)
        Jobs command = new Jobs()
        command.rdTool = rdTool
        command.rdOutput = out

        def opts = new Jobs.Purge()
        opts.batchSize = batch
        opts.max = max
        opts.confirm = true
        def listOpts = new JobListOptions()
        listOpts.setJob(job)
        listOpts.setProject 'ProjectName'


        def iter = 0
        when:
        def result = command.purge(opts, new JobOutputFormatOption(), new JobFileOptions(), listOpts)

        then:
        1 * api.listJobs('ProjectName', job, null, null, null) >> {
            Calls.response((1..total).collect { new JobItem(id: "fakeid_$it") })
        }
        (expect.size()) * api.deleteJobsBulk({ it.ids.size() == expect[iter++] }) >> {
            Calls.response(new DeleteJobsResult(allsuccessful: true))
        }
        0 * api._(*_)
        result

        where:
            job | batch | total | max || expect
            'a' | -1    | 5     | -1  || [5]
            'a' | 1     | 5     | -1  || [1, 1, 1, 1, 1,]
            'a' | 2     | 5     | -1  || [2, 2, 1,]
            'a' | 3     | 5     | -1  || [3, 2]
            'a' | 4     | 5     | -1  || [4, 1]
            'a' | 5     | 5     | -1  || [5]
            'a' | 6     | 5     | -1  || [5]
            'a' | 99    | 5     | -1  || [5]

            'a' | -1    | 5     | 99  || [5]
            'a' | -1    | 5     | 5   || [5]
            'a' | -1    | 5     | 4   || [4]
            'a' | -1    | 5     | 1   || [1]

            'a' | 1     | 5     | 5   || [1, 1, 1, 1, 1,]
            'a' | 1     | 5     | 4   || [1, 1, 1, 1,]
            'a' | 1     | 5     | 1   || [1,]

            'a' | 2     | 5     | 5   || [2, 2, 1,]
            'a' | 2     | 5     | 4   || [2, 2,]
            'a' | 2     | 5     | 3   || [2, 1,]
            'a' | 2     | 5     | 1   || [1,]

            'a' | 3     | 5     | 99  || [3, 2]
            'a' | 3     | 5     | 5   || [3, 2]
            'a' | 3     | 5     | 3   || [3]

            'a' | 99    | 5     | 99  || [5]
    }

    def "job purge invalid input"() {
        given:
        def api = Mock(RundeckApi)

        def opts = new Jobs.Purge()
        opts.confirm = true
        Jobs jobs = new Jobs()
        when:
        jobs.purge(opts, new JobOutputFormatOption(), new JobFileOptions(), new JobListOptions())

        then:
        InputError e = thrown()
        e.message == 'must specify -i, or -j/-g/-J/-G to specify jobs to delete.'

    }

    def "jobs info outformat"() {
        given:


        def api = Mock(RundeckApi)
        RdTool rdTool = setupMock(api)
        def out = Mock(CommandOutput)
        Jobs command = new Jobs()
        command.rdTool = rdTool
        command.rdOutput = out

        def opts = new JobOutputFormatOption()
        opts.outputFormat = outFormat


        when:
        command.info('123', opts)

        then:
        1 * api.getJobInfo('123') >> Calls.response(new ScheduledJobItem(id: '123', href: 'monkey'))
        1 * out.output([result])


        where:
        outFormat   | result
        '%id %href' | '123 monkey'
    }

    def "job load with errors produces output"() {
        given:
        def opts = new JobFileOptions()
        opts.format= JobFileOptions.Format.yaml
        opts.file=tempFile


        def api = Mock(RundeckApi)
        RdTool rdTool = setupMock(api)
        def out = Mock(CommandOutput)
        Jobs command = new Jobs()
        command.rdTool=rdTool
        command.rdOutput=out

        when:
        command.load(new JobLoadOptions(), opts,new ProjectNameOptions(project:'ProjectName'),new VerboseOption())

        then:
        1 * api.loadJobs('ProjectName', _, 'yaml', _, _) >>
                Calls.response(new ImportResult(succeeded: [], skipped: [], failed: [
                        new JobLoadItem(error: 'Test Error', name: 'Job Name')
                ]
                )
                )
        0 * api._(*_)
        1 * out.info('1 Jobs Failed:\n')
        1 * out.output(['[id:?] Job Name\n\t:Test Error'])
        0 * out._(*_)

    }

    def "job load with errors verbose output"() {
        given:
            def api = Mock(RundeckApi)
            RdTool rdTool = setupMock(api)
            def out = Mock(CommandOutput)
            Jobs command = new Jobs()
            command.rdTool=rdTool
            command.rdOutput=out
            def opts = new JobFileOptions()
            opts.format= JobFileOptions.Format.yaml
            opts.file=tempFile

            def resultItem = new JobLoadItem(error: 'Test Error', name: 'Job Name')
        when:
            command.load(new JobLoadOptions(), opts,new ProjectNameOptions(project:'ProjectName'),new VerboseOption(verbose: true))

        then:
            1 * api.loadJobs('ProjectName', _, 'yaml', _, _) >>
            Calls.response(new ImportResult(succeeded: [], skipped: [], failed: [resultItem]))
            0 * api._(*_)
            1 * out.info('1 Jobs Failed:\n')
            1 * out.output([resultItem])
            0 * out._(*_)

    }

    def "job forecast"() {
        given:
        def api = Mock(RundeckApi)
        RdTool rdTool = setupMock(api,31)
        def out = Mock(CommandOutput)
        Jobs command = new Jobs()
        command.rdTool=rdTool
        command.rdOutput=out


        def date = new Date()

        when:
        command.forecast('123','1d',1)

        then:
        1 * api.getJobForecast('123','1d',1) >> Calls.response(new ForecastJobItem(futureScheduledExecutions: [date]))
        0 * api._(*_)
        1 * out.output('Forecast:')
        1 * out.output([date])

    }


}
