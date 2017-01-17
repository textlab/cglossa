#r "../node_modules/fable-core/Fable.Core.dll"
#load "SearchEngine.fsx"

namespace App

open Fable.Core.JsInterop

module Model =

///////////////////////////////
// Corpus and languages
///////////////////////////////

    module LanguageCode =

        type T = LanguageCode of string

        let validate = importMember<string->bool> "iso-639-1"

        let create (twoCharCode: string) =
            // Use the iso-639-1 npm module to validate the language code
            if validate twoCharCode then
                Some (LanguageCode twoCharCode)
            else
                None

        let value (LanguageCode c) = c

    type Language = { Code : LanguageCode.T }

    module AlignedLanguageList =

        type T = AlignedLanguageList of Language list

        let create (languages: Language list) =
            // An aligned corpus must have at least two aligned languages
            if List.length languages >= 2 then
                Some (AlignedLanguageList languages)
            else
                None

        let value (AlignedLanguageList languages) = languages

    type Languages =
        | SingleLanguage of Language
        | AlignedLanguages of AlignedLanguageList.T

    // We list search engines in a separate file to make it easy to extend
    // the list in individual Glossa installations
    open SearchEngine

    type Url = Url of string

    type CorpusCode = CorpusCode of string

    type CorpusName = CorpusName of string

    type Corpus = { 
        Code : CorpusCode
        Name : CorpusName
        Languages : Languages
        LogoOpt : Url option
        MetadataCategoriesOpt : (string list) option
        SearchEngine : SearchEngine
    }

///////////////////////////////
// Searches and results
///////////////////////////////

    type Query = { 
        QueryString : string
        Language : Language
    }

    type SearchId = SearchId of int

    type Search = { 
        // the ID is not set until the search is saved
        IdOpt : SearchId option
        Queries : Query list
        MetadataValueIds : int list
    }

    type Result = string

    type SortKey = 
        | Position
        | Match
        | LeftImmediate
        | LeftWide
        | RightImmediate
        | RightWide

///////////////////////////////
// Application state
///////////////////////////////

    type SearchView = 
        | SimpleSearchView
        | ExtendedSearchView
        | CQPSearchView

    type FrontPageState = None

    type SearchPageState = {
        Corpus: Corpus
        SearchView: SearchView
        Queries: Query list
    }

    type ResultPageState = {
        Corpus: Corpus
        SearchView: SearchView
        Queries: Query list
        Results: Result list
    }

////////////////////////////////////////////
// Model
////////////////////////////////////////////

    type Model =
        | FrontPage of FrontPageState
        | SearchPage of SearchPageState
        | ResultPage of ResultPageState
        // displayedQueries : List Query
        // isNarrowView : Bool
        // page : Page
        // maybeShouldShowMetadata : Maybe Bool

(*

    type Page
        = FrontPage
        | SearchPage Corpus SearchView
        | ResultPage Corpus SearchView (List Result)




--------------------------------------------
-- ResultPage types
--------------------------------------------

type ResultViewState = { sortKey : SortKey }


type alias Result =
    { text : String }


isShowingMetadata : Model -> Corpus -> Bool
isShowingMetadata model corpus =
    case corpus.maybeMetadataCategories of
        Nothing ->
            -- Don't show metadata if the corpus doesn't have any (duh!)
            False

        Just _ ->
            case model.maybeShouldShowMetadata of
                Just shouldShowMetadata ->
                    {- If maybeShouldShowMetadata is a Just, the user has explicitly chosen
                       whether to see metadata, so we respect that unconditionally
                    -}
                    shouldShowMetadata

                Nothing ->
                    {- Now we know that we have metadata, and that the user has not explicitly
                       chosen whether to see them. If we are showing search results, we hide the
                       metadata if the window is narrow; if instead we are showing the search page,
                       we show the metadata regardless of window size.
                    -}
                    case model.page of
                        FrontPage ->
                            False

                        ResultPage _ _ _ ->
                            -- TODO: Dynamically set isNarrowView on model
                            not model.isNarrowView

                        SearchPage _ _ ->
                            True

*)