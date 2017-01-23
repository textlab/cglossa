#load "./Model.fsx"

namespace App

open Model

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
        | ResetForm of Corpus * SearchView
        ////////////////////////////
        // ResultPage messages
        ////////////////////////////
        | SortBy of SortKey
