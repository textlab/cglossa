#r "../node_modules/fable-core/Fable.Core.dll"
#load "SearchEngine.fsx"

namespace App

open Fable.Core.JsInterop

module Model =
    type CorpusCode = CorpusCode of string

    module LanguageCode =

        type T = LanguageCode of string
        let getName = importMember<string->string> "iso-639-1"
        let create code =
            if System.String.IsNullOrEmpty((getName code)) then
                None
            else
                Some (LanguageCode code)

    type Language = { Code : LanguageCode.T }

    type Languages =
        | SingleLanguage of Language
        // alignment requires at least two languages
        | AlignedLanguages of Language * Language * Language list

    type Url = Url of string

    open SearchEngine

    type T = { 
        Code : CorpusCode
        Name : string
        Languages : Languages
        LogoOpt : Url option
        MetadataCategoriesOpt : (string list) option
        SearchEngine : SearchEngine
    }

(* 


    type FrontPageState = None

    type Query = { 
        query : string
        lang : LanguageCode
    }

    type Search = { 
        // the ID is not set until the search is saved
        maybeId : int option
        queries : Query list
        metadataValueIds : int list
    }

    type SearchPageState = {
        Corpus: Corpus
        SearchView: SearchView
        Queries: Query list
    }

    type Result = string

    type ResultPageState = {
        Corpus: Corpus
        SearchView: SearchView
        Results: Result list
    }

////////////////////////////////////////////
// Common types
////////////////////////////////////////////

    type Model =
        | FrontPage of FrontPageState
        | SearchPage of SearchPageState
        | ResultPage of ResultPageState
        displayedQueries : List Query
        isNarrowView : Bool
        page : Page
        maybeShouldShowMetadata : Maybe Bool
    }


    type Page
        = FrontPage
        | SearchPage Corpus SearchView
        | ResultPage Corpus SearchView (List Result)


    type SearchView
        = SimpleSearchView
        | ExtendedSearchView
        | CQPSearchView


--------------------------------------------
-- ResultPage types
--------------------------------------------

type SortKey
    = Position
    | Match
    | LeftImmediate
    | LeftWide
    | RightImmediate
    | RightWide

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