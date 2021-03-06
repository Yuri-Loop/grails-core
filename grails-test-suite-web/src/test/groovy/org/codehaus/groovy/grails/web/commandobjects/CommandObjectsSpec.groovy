package org.codehaus.groovy.grails.web.commandobjects

import grails.artefact.Artefact
import grails.test.mixin.TestFor
import grails.util.ClosureToMapPopulator

import org.codehaus.groovy.grails.validation.ConstraintsEvaluator
import org.codehaus.groovy.grails.validation.ConstraintsEvaluatorFactoryBean

import spock.lang.Specification

@TestFor(TestController)
class CommandObjectsSpec extends Specification {
    
    def setupSpec() {
        defineBeans {
            theAnswer(Integer, 42)
            "${ConstraintsEvaluator.BEAN_NAME}"(ConstraintsEvaluatorFactoryBean) {
                def constraintsClosure = {
                    isProg inList: ['Emerson', 'Lake', 'Palmer']
                }
                defaultConstraints = new ClosureToMapPopulator().populate(constraintsClosure)
            }
        }
    }

    void "Test command object with date binding"() {
        setup:
        def expectedCalendar = Calendar.instance
        expectedCalendar.clear()
        expectedCalendar.set Calendar.DAY_OF_MONTH, 3
        expectedCalendar.set Calendar.MONTH, Calendar.MAY
        expectedCalendar.set Calendar.YEAR, 1973
        def expectedDate = expectedCalendar.time

        when:
        controller.params.birthday = "struct"
        controller.params.birthday_day = "03"
        controller.params.birthday_month = "05"
        controller.params.birthday_year = "1973"
        def model = controller.closureActionWithDate()
        def birthday = model.command?.birthday

        then:
        model.command
        !model.command.hasErrors()
        birthday
        expectedDate == birthday

        when:
        controller.params.birthday = "struct"
        controller.params.birthday_day = "03"
        controller.params.birthday_month = "05"
        controller.params.birthday_year = "1973"
        model = controller.methodActionWithDate()
        birthday = model.command?.birthday

        then:
        model.command
        !model.command.hasErrors()
        birthday
        expectedDate == birthday
    }

    void 'Test that rejected binding value survives validation'() {
        when:
        controller.params.width = 'some bad value'
        controller.params.height = 42
        def model = controller.methodActionWithWidgetCommand()
        def widget = model.widget
        def err = widget.errors

        then:
        widget.height == 42
        widget.width == null
        widget.errors.errorCount == 2
        widget.errors.getFieldError('width').rejectedValue == 'some bad value'
    }
    void 'Test non validateable command object'() {
        when:
        controller.params.name = 'Beardfish'
        def model = controller.methodActionWithNonValidateableCommandObject()

        then:
        model.commandObject.name == 'Beardfish'

        when:
        controller.params.name = "Spock's Beard"
        model = controller.closureActionWithNonValidateableCommandObject()

        then:
        model.commandObject.name == "Spock's Beard"
    }

    void 'Test binding to a command object setter property'() {
        when:
        controller.params.someValue = 'My Value'
        def model = controller.methodActionWithSomeCommand()

        then:
        model.commandObject.someValue == 'My Value'
    }

    void "Test binding to multiple command objects"() {
        when:
        controller.params.name = 'Emerson'
        def model = controller.methodActionWithMultipleCommandObjects()

        then:
        model.person
        model.artist
        model.artist.name == 'Emerson'
        model.person.name == 'Emerson'

        when:
        controller.params.name = 'Emerson'
        model = controller.closureActionWithMultipleCommandObjects()

        then:
        model.person
        model.artist
        model.artist.name == 'Emerson'
        model.person.name == 'Emerson'
    }

    void "Test binding to multiple command objects with param name prefixes"() {
        when:
        controller.params.person = [name: 'Emerson']
        controller.params.artist = [name: 'Lake']
        def model = controller.methodActionWithMultipleCommandObjects()

        then:
        model.person
        model.artist
        model.artist.name == 'Lake'
        model.person.name == 'Emerson'

        when:
        controller.params.person = [name: 'Emerson']
        controller.params.artist = [name: 'Lake']
        model = controller.closureActionWithMultipleCommandObjects()

        then:
        model.person
        model.artist
        model.artist.name == 'Lake'
        model.person.name == 'Emerson'
    }

    void "Test clearErrors"() {
        when:
        def model = controller.methodActionWithArtist()

        then:
        model.artist
        model.artist.name == null
        model.artist.hasErrors()
        model.artist.errors.errorCount == 1

        when:
        model.artist.clearErrors()

        then:
        !model.artist.hasErrors()
        model.artist.errors.errorCount == 0
    }

    void "Test nullability"() {
        when:
        def model = controller.methodActionWithArtist()
        def nameErrorCodes = model.artist?.errors?.getFieldError('name')?.codes?.toList()

        then:
        model.artist
        model.artist.name == null
        nameErrorCodes
        'artistCommand.name.nullable.error' in nameErrorCodes

        when:
        model = controller.closureActionWithArtist()
        nameErrorCodes = model.artist?.errors?.getFieldError('name')?.codes?.toList()

        then:
        model.artist
        model.artist.name == null
        nameErrorCodes
        'artistCommand.name.nullable.error' in nameErrorCodes
    }

    void 'Test beforeValidate gets invoked'() {
        when:
        def model = controller.methodAction()
        def person = model.person

        then:
        1 == person.beforeValidateCounter
    }

    void 'Test constraints property'() {
        when:
        def model = controller.methodAction()
        def person = model.person
        def constrainedProperties = person.constraints
        def nameConstrainedProperty = constrainedProperties.name
        def matchesProperty = nameConstrainedProperty.matches

        then:
        /[A-Z]+/ == matchesProperty
    }

    void "Test command object gets autowired"() {
        when:
        def model = controller.methodAction()

        then:
        model.person.theAnswer == 42

        when:
        model = controller.closureAction()

        then:
        model.person.theAnswer == 42
    }

    void 'Test bindable command object constraint'() {
        when:
        controller.params.name = 'JFK'
        controller.params.city = 'STL'
        controller.params.state = 'Missouri'
        def model = controller.methodAction()

        then:
        !model.person.hasErrors()
        model.person.name == 'JFK'
        model.person.state == 'Missouri'
        model.person.city == null
    }

    void 'Test subscript operator on command object errors'() {
        when:
        controller.params.name = 'Maynard'
        def model = controller.closureAction()

        then:
        model.person.hasErrors()
        model.person.name == 'Maynard'
        'matches.invalid.name' in model.person.errors['name'].codes
    }

    void "Test validation"() {
        when:
        controller.params.name = 'JFK'
        def model = controller.methodAction()

        then:
        !model.person.hasErrors()
        model.person.name == 'JFK'

        when:
        controller.params.name = 'JFK'
        model = controller.closureAction()

        then:
        !model.person.hasErrors()
        model.person.name == 'JFK'

        when:
        controller.params.name = 'Maynard'
        model = controller.closureAction()

        then:
        model.person.hasErrors()
        model.person.name == 'Maynard'

        when:
        controller.params.name = 'Maynard'
        model = controller.methodAction()

        then:
        model.person.hasErrors()
        model.person.name == 'Maynard'
    }

    void "Test validation with inherited constraints"() {

        when:
        controller.params.name = 'Emerson'
        controller.params.bandName = 'Emerson Lake and Palmer'
        def model = controller.closureActionWithArtistSubclass()

        then:
        model.artist
        model.artist.name == 'Emerson'
        model.artist.bandName == 'Emerson Lake and Palmer'
        !model.artist.hasErrors()

        when:
        controller.params.name = 'Emerson'
        controller.params.bandName = 'Emerson Lake and Palmer'
        model = controller.methodActionWithArtistSubclass()

        then:
        model.artist
        model.artist.name == 'Emerson'
        model.artist.bandName == 'Emerson Lake and Palmer'
        !model.artist.hasErrors()

        when:
        controller.params.clear()
        model = controller.closureActionWithArtistSubclass()

        then:
        model.artist
        model.artist.hasErrors()
        model.artist.errors.errorCount == 2

        when:
        model = controller.methodActionWithArtistSubclass()

        then:
        model.artist
        model.artist.hasErrors()
        model.artist.errors.errorCount == 2
    }

    void "Test validation with shared constraints"() {
        when:
        controller.params.name = 'Emerson'
        def model = controller.closureActionWithArtist()

        then:
        model.artist
        !model.artist.hasErrors()

        when:
        controller.params.name = 'Emerson'
        model = controller.methodActionWithArtist()

        then:
        model.artist
        !model.artist.hasErrors()

        when:
        controller.params.name = 'Hendrix'
        model = controller.closureActionWithArtist()

        then:
        model.artist
        model.artist.hasErrors()

        when:
        controller.params.name = 'Hendrix'
        model = controller.methodActionWithArtist()

        then:
        model.artist
        model.artist.hasErrors()
    }


    void 'Test command object that is a precompiled @Validatable'() {
        when:
        def model = controller.methodActionWithValidateableParam()

        then:
        model.commandObject.hasErrors()

        when:
        controller.params.name = 'mixedCase'
        model = controller.methodActionWithValidateableParam()

        then:
        model.commandObject.hasErrors()

        when:
        controller.params.name = 'UPPERCASE'
        model = controller.methodActionWithValidateableParam()

        then:
        !model.commandObject.hasErrors()
    }

    void "Test a command object that does not have a validate method at compile time but does at runtime"() {
        when:
        def model = controller.methodActionWithNonValidateableCommandObjectWithAValidateMethod()

        then:
        0 == model.co.validationCounter

        when:
        model = controller.closureActionWithNonValidateableCommandObjectWithAValidateMethod()

        then:
        0 == model.co.validationCounter

        when:
        ClassWithNoValidateMethod.metaClass.validate = { -> ++ delegate.validationCounter }
        model = controller.methodActionWithNonValidateableCommandObjectWithAValidateMethod()

        then:
        1 == model.co.validationCounter

        when:
        model = controller.closureActionWithNonValidateableCommandObjectWithAValidateMethod()

        then:
        1 == model.co.validationCounter
    }

}

@Artefact('Controller')
class TestController {
    def closureAction = { PersonCommand p ->
        [person: p]
    }

    def methodAction(PersonCommand p) {
        [person: p]
    }

    def methodActionWithDate(DateComamndObject co) {
        [command: co]
    }

    def closureActionWithDate = { DateComamndObject co ->
        [command: co]
    }

    def closureActionWithArtist = { ArtistCommand a ->
        [artist: a]
    }

    def methodActionWithArtist(ArtistCommand a) {
        [artist: a]
    }

    def methodActionWithArtistSubclass(ArtistSubclass a) {
        [artist: a]
    }

    def closureActionWithArtistSubclass = { ArtistSubclass a ->
        [artist: a]
    }

    def closureActionWithMultipleCommandObjects = { PersonCommand p, ArtistCommand a ->
        [person: p, artist: a]
    }

    def methodActionWithMultipleCommandObjects(PersonCommand p, ArtistCommand a)  {
        [person: p, artist: a]
    }

    def methodActionWithSomeCommand(SomeCommand co) {
        [commandObject: co]
    }

    def methodActionWithWidgetCommand(WidgetCommand co) {
        [widget: co]
    }

    def closureActionWithNonValidateableCommandObjectWithAValidateMethod = { ClassWithNoValidateMethod co ->
        [co: co]
    }

    def closureActionWithNonValidateableCommandObject = { NonValidateableCommand co ->
        [commandObject: co]
    }

    def methodActionWithValidateableParam(SomeValidateableClass svc) {
        [commandObject: svc]
    }

    def methodActionWithNonValidateableCommandObjectWithAValidateMethod(ClassWithNoValidateMethod co) {
        [co: co]
    }

    def methodActionWithNonValidateableCommandObject(NonValidateableCommand co) {
        [commandObject: co]
    }
}

class DateComamndObject {
    Date birthday
}

class WidgetCommand {
    Integer width
    Integer height

    static constraints = { height range: 1..10 }
}

class SomeCommand {
    private String someFieldWithNoSetter

    void setSomeValue(String val) {
        someFieldWithNoSetter = val
    }

    String getSomeValue() {
        someFieldWithNoSetter
    }
}

class ArtistCommand {
    String name
    static constraints = { name shared: 'isProg' }
}

class ArtistSubclass extends ArtistCommand {
    String bandName
    static constraints = { bandName matches: /[A-Z].*/ }
}
abstract class MyAbstractController {
    def index = { [name: 'Abstract Parent Controller'] }
}
class SubClassController extends MyAbstractController {
    def index = { [name: 'Subclass Controller'] }
}

class PersonCommand {
    String name
    def theAnswer
    def beforeValidateCounter = 0

    String city
    String state

    def beforeValidate() {
        ++beforeValidateCounter
    }

    static constraints = {
        name matches: /[A-Z]+/
        bindable: false
        city nullable: true, bindable: false
        state nullable: true
    }
}





