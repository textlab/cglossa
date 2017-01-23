#load "Model.fsx"
#load "Msg.fsx"

namespace App

open Model
open Msg

module Update =

    let resetQueries corpus =
        let language = 
            match corpus.Languages with
                | Model.SingleLanguage lang -> lang
                | Model.AlignedLanguages languageList -> 
                    // When we create an AlignedLanguageList, we make sure that it contains
                    // at least 2 items, so using List.head here should be safe
                    languageList |> Model.AlignedLanguageList.value |> List.head
        [ { QueryString = ""; Language = language } ]

    let update (msg: Msg.Msg) (model: Model.Model) =
        match msg with

            /////////////////////////////////////////////
            // Common messages
            /////////////////////////////////////////////

            | ClickedSimpleSearchView ->
                model, []

            | ClickedExtendedSearchView ->
                model, []

            | ClickedCQPSearchView ->
                model, []

            | HideMetadata ->
                model, []

            | ShowMetadata ->
                model, []

            | ResetForm (corpus, searchView) ->
                SearchPage { 
                    Corpus = corpus
                    SearchView = searchView
                    Queries = resetQueries corpus 
                }
                , []

            /////////////////////////////////////////////
            // ResultPage messages
            /////////////////////////////////////////////

            | SortBy sortKey ->
                model, []
