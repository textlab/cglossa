#r "../node_modules/fable-core/Fable.Core.dll"
#r "../node_modules/fable-react/Fable.React.dll"
#r "../node_modules/fable-elmish/Fable.Elmish.dll"
#r "../node_modules/fable-elmish-react/Fable.Elmish.React.dll"

namespace App

open Fable.Core
open Fable.Core.JsInterop
open Fable.Import
open Fable.Import.Browser

open System

module Main =

  type Model =
    { Input: string }

    static member initial =
      #if DEV_HMR
      // This section is used to maintain state between HMR
      if not <| isNull (unbox window?storage) then
        unbox window?storage
      else
        let model = { Input = "" }
        window?storage <- model
        model
      #else
      { Input = "" }
      #endif

  let init _ = Model.initial, []

  // Actions supported by the application
  type Msg =
    | ChangeInput of string

  let update (msg:Msg) (model: Model) =
    let model', msg' =
      match msg with
      | ChangeInput s ->
        { model with Input = s } , []

    #if DEV_HMR
    // Update the model in storage
    window?storage <- model'
    #endif

    model', msg'

  /// View

  open Fable.Helpers.React
  open Fable.Helpers.React.Props

  let view model dispatch =
    div
      []
      [
        label
          []
          [unbox "Enter name: "]
        input
          [ OnInput (fun e -> ChangeInput (unbox e?target?value) |> dispatch ) ]
          []
        br [] []
        span
          []
          [unbox (sprintf "Hello %s" model.Input)]
      ]

  open Elmish
  open Elmish.React

  #if DEV_HMR
  let start contentNodeClass = Elmish.Program.mkProgram init update view
                               |> Program.withConsoleTrace
                               |> Program.withReact contentNodeClass
                               |> Program.run
  #else
  let start contentNodeClass = Elmish.Program.mkProgram init update view
                               |> Program.withReact contentNodeClass
                               |> Program.run
  #endif