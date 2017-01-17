#load "./Model.fsx"

namespace App

module Msg =

    type Msg =
        ////////////////////////////
        // Common messages
        ////////////////////////////
        | ClickedSimpleSearchView
        | ClickedExtendedSearchView
        | ClickedCQPSearchView
        | HideMetadata
        | ShowMetadata
        | ResetForm of Model.Corpus * Model.SearchView
        ////////////////////////////
        // ResultPage messages
        ////////////////////////////
        | SortBy of Model.SortKey
